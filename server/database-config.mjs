import {readFileSync} from 'node:fs';
export function databaseConfig(env=process.env) {
 let value=(env.DATABASE_URL||'').trim();
 if ((value.startsWith('"')&&value.endsWith('"'))||(value.startsWith("'")&&value.endsWith("'"))) value=value.slice(1,-1).trim();
 let url;try {url=new URL(value);} catch {throw Object.assign(new Error(),{code:'ARTII_DATABASE_URL'});}
 if(!['postgres:','postgresql:'].includes(url.protocol))throw Object.assign(new Error(),{code:'ARTII_DATABASE_URL'});
 if(url.port==='6543')throw Object.assign(new Error(),{code:'ARTII_TRANSACTION_POOL'});
 for(const key of ['sslmode','sslcert','sslkey','sslrootcert'])url.searchParams.delete(key);
 const local=['localhost','127.0.0.1','::1','[::1]'].includes(url.hostname);
 const insecure=env.DATABASE_TLS==='off';
 if(insecure&&(!local||env.NODE_ENV==='production'))throw Object.assign(new Error(),{code:'ARTII_TLS_REQUIRED'});
 return {connectionString:url.toString(),ssl:insecure?false:{rejectUnauthorized:true,...(env.DATABASE_CA_FILE?{ca:readFileSync(env.DATABASE_CA_FILE.trim(),'utf8')}:{})},connectionTimeoutMillis:15000,statement_timeout:15000};
}
export function databaseStartupError(error,stage) {
 const code=typeof error?.code==='string'&&/^[A-Z0-9_]{2,60}$/.test(error.code)?error.code:'UNKNOWN';
 const hints={
 ARTII_DATABASE_URL:'Set DATABASE_URL to the PostgreSQL connection URI from Supabase Connect, not the project HTTPS URL or API key.',
 ARTII_TRANSACTION_POOL:'Use the Supabase Session pooler on port 5432. Transaction pooling on port 6543 cannot retain the writer lock.',
 ARTII_TLS_REQUIRED:'TLS certificate verification is required for a hosted database.',
 ARTII_WRITER_ACTIVE:'Another Artii instance holds the database writer lock. Stop the old instance and deploy exactly one instance.',
 ENETUNREACH:'The database network is unreachable. For an IPv4 host, use the Supabase Session pooler URI on port 5432 instead of the direct IPv6 address.',
 EHOSTUNREACH:'The database host is unreachable. Check the Supabase Session pooler connection and network restrictions.',
 ENOTFOUND:'Database hostname could not be resolved. Copy the Session pooler URI from Supabase Connect.',
 EAI_AGAIN:'Temporary database DNS failure. Retry and check the database hostname.',
 ECONNREFUSED:'Database connection refused. Check the host, port, project status, and network restrictions.',
 ETIMEDOUT:'Database connection timed out. Check the host, port, project status, and network restrictions.',
 '28P01':'Database password authentication failed. Check the database password and URI-encode special characters.',
 '28000':'Database authentication rejected. Copy the complete Session pooler username, including the project reference.',
 '42501':'Database role lacks required privileges. Use the project database owner for initial schema migration.',
 '3D000':'Database does not exist. Verify the database name in the connection URI.',
 SELF_SIGNED_CERT_IN_CHAIN:'Database CA is not trusted. Set DATABASE_CA_FILE to the Supabase CA certificate uploaded as a Render secret file.',
 DEPTH_ZERO_SELF_SIGNED_CERT:'Database certificate is self-signed. Supply the provider CA using DATABASE_CA_FILE.',
 UNABLE_TO_VERIFY_LEAF_SIGNATURE:'Database certificate chain cannot be verified. Supply the provider CA using DATABASE_CA_FILE.',
 UNABLE_TO_GET_ISSUER_CERT_LOCALLY:'Database issuer is not trusted. Supply the provider CA using DATABASE_CA_FILE.',
 CERT_HAS_EXPIRED:'Database certificate has expired. Check the provider certificate and configured CA.',
 ENOENT:'The configured database CA file does not exist. Check the Render secret file path.'
 };
 // Never include raw driver messages, SQL details or connection strings: these may contain credentials or user data.
 return `Database startup failed at ${stage} [${code}]. ${hints[code]||'Check database configuration and migration compatibility. Share this stage and code, never the database URI or password.'}`;
}
