import {readFileSync} from 'node:fs';
import {openPostgres} from './postgres.mjs';
// Explicit offline import only. Refuses to overwrite a non-empty managed database.
const file=process.argv[2];if(!file)throw Error('Usage: node --env-file=../.env import-json.mjs /path/to/artii.json');
if(!process.env.DATABASE_URL)throw Error('DATABASE_URL is required');
const store=openPostgres();try{
 if(Object.values(store.initial).some(list=>list.length))throw Error('Destination is not empty; import refused');
 const db=JSON.parse(readFileSync(file,'utf8'));store.save(db);console.log('Imported durable records. Copy the associated media directory to the persistent disk separately.');
}finally{store.close();}
