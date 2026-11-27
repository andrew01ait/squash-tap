import {b64} from './interchange.mjs?v=0.16.0';
export const MAX_BACKUP=20*1024*1024;
export async function validateBackup(text){
 if(typeof text!=='string'||text.length>MAX_BACKUP)throw Error('Backup is too large');
 let a;try{a=JSON.parse(text);}catch{throw Error('Choose a complete Keep Score backup JSON file');}
 if(a.application!=='Keep Score'||a.package!=='at.andrew.squashtap'||a.schema!==1||typeof a.createdAt!=='string'||!Number.isFinite(Date.parse(a.createdAt))||typeof a.payload!=='string'||!/^[-\w]+$/.test(a.payload)||typeof a.sha256!=='string'||!/^[-\w]{43}$/.test(a.sha256))throw Error('Unsupported Keep Score backup');
 const digest=b64(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(a.payload))));if(digest!==a.sha256)throw Error('Backup checksum does not match. Select the original file.');
 return {createdAt:a.createdAt,text};
}
export const datedFile=suffix=>new Date().toLocaleDateString('sv-SE').replaceAll('-','')+'_KeepScore_'+suffix;
