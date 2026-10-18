import test from 'node:test';
import assert from 'node:assert/strict';
import {databaseConfig,databaseStartupError} from './database-config.mjs';
import pg from 'pg';
test('raw database passwords reach the driver unchanged and override URL passwords',()=>{
 for(const password of ['p@ss:/?#%+word','literal%40value',' spaces and "quotes" ','é🔐']){
  const c=databaseConfig({DATABASE_URL:'postgres://postgres.project:old@db.example:5432/postgres?password=stale',DATABASE_PASSWORD:password});
  const client=new pg.Client(c);
  assert.equal(client.connectionParameters.password,password);
  assert.equal(client.connectionParameters.user,'postgres.project');
  assert.equal(client.connectionParameters.host,'db.example');
  assert.equal(client.connectionParameters.ssl.rejectUnauthorized,true);
 }
});
test('blank separate password preserves existing URI credentials',()=>{
 const c=databaseConfig({DATABASE_URL:'postgres://u:p%40ss@db.example/postgres',DATABASE_PASSWORD:''});
 assert.equal(new pg.Client(c).connectionParameters.password,'p@ss');
});
test('database URL normalizes pasted quotes and preserves verified TLS',()=>{
 const c=databaseConfig({DATABASE_URL:' "postgresql://user:password@db.example:5432/postgres?sslmode=require" '});
 assert.equal(c.connectionString,'postgresql://user:password@db.example:5432/postgres');assert.equal(c.ssl.rejectUnauthorized,true);
});
test('rejects wrong connection types and insecure hosted connections',()=>{
 for(const [env,code] of [[{DATABASE_URL:'https://example.com'},'ARTII_DATABASE_URL'],[{DATABASE_URL:'postgres://u:p@db.example:6543/postgres'},'ARTII_TRANSACTION_POOL'],[{DATABASE_URL:'postgres://u:p@db.example/postgres',DATABASE_TLS:'off'},'ARTII_TLS_REQUIRED']])assert.throws(()=>databaseConfig(env),{code});
});
test('startup diagnostics identify cause without exposing driver secrets',()=>{
 for(const code of ['28P01','42501','ENETUNREACH','SELF_SIGNED_CERT_IN_CHAIN','ARTII_WRITER_ACTIVE','42601']){
 const text=databaseStartupError({code,message:'password=TOPSECRET',detail:'private user data'},'migration');
 assert.ok(text.includes(code));assert.ok(text.includes('migration'));assert.ok(!text.includes('TOPSECRET'));assert.ok(!text.includes('private user data'));
 }
});
