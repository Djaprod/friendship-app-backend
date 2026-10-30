import pg from 'pg';
import {open,mkdir,rename,unlink} from 'node:fs/promises';
import {dirname} from 'node:path';
import {databaseConfig} from './database-config.mjs';

// Media bytes are stored separately from the frequently synchronized JSON state.
// A local file is only a cache; authenticated reads can restore it after redeploys.
export function createDurableMedia(pool=new pg.Pool({...databaseConfig(),max:2,idleTimeoutMillis:10000})){
 pool.on?.('error',()=>console.error('Media database connection interrupted; requests will retry.'));
 return {
  async put(id,path){
   const client=await pool.connect();let file;
   try{
    file=await open(path,'r');await client.query('BEGIN');
    await client.query('DELETE FROM artii.media_chunks WHERE media_id=$1',[id]);
    const buffer=Buffer.alloc(4*1024*1024);let part=0;
    while(true){const {bytesRead}=await file.read(buffer,0,buffer.length,null);if(!bytesRead)break;await client.query('INSERT INTO artii.media_chunks(media_id,part,bytes) VALUES($1,$2,$3)',[id,part++,buffer.subarray(0,bytesRead)]);}
    await client.query('COMMIT');
   }catch(e){await client.query('ROLLBACK').catch(()=>{});throw e}finally{await file?.close();client.release();}
  },
  async restore(id,path,expectedSize){
   await mkdir(dirname(path),{recursive:true});const tmp=path+'.restore-'+crypto.randomUUID();let file;let size=0;
   try{
    file=await open(tmp,'wx');
    for(let part=0;;part++){
     const result=await pool.query('SELECT bytes FROM artii.media_chunks WHERE media_id=$1 AND part=$2',[id,part]);
     if(!result.rows.length)break;
     const bytes=Buffer.from(result.rows[0].bytes);await file.writeFile(bytes);size+=bytes.length;
     if(size>expectedSize)throw Error('Invalid stored media size');
    }
    await file.close();file=null;
    if(size!==expectedSize||size===0){await unlink(tmp);return false;}
    await rename(tmp,path);return true;
   }catch(e){await file?.close();await unlink(tmp).catch(()=>{});throw e;}
  },
  async remove(id){await pool.query('DELETE FROM artii.media_chunks WHERE media_id=$1',[id]);},
  close(){return pool.end();}
 };
}
