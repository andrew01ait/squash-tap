import {sportName,rowRules,scorePairs} from './sports.mjs?v=0.16.3';
import {rowWinner,localDate} from './interchange.mjs?v=0.16.0';
import {datedFile} from './backup.mjs?v=0.16.0';
function lines(ctx,text,width){const out=[];let line='';for(const char of [...text]){if(line&&ctx.measureText(line+char).width>width){out.push(line.trim());line=char;}else line+=char;}if(line)out.push(line.trim());return out;}
function label(ctx,text,x,y,width,size,maxLines=6){let rows;do{ctx.font='600 '+size+'px system-ui';rows=lines(ctx,text,width);if(rows.length<=maxLines)break;size--;}while(size>12);ctx.textAlign='center';for(let i=0;i<rows.length;i++)ctx.fillText(rows[i],x,y+i*size*1.22);}
export async function resultCardBlob(row,opponent,myName='Me'){
 const f=rowRules(row),canvas=document.createElement('canvas');canvas.width=1080;canvas.height=1280;const c=canvas.getContext('2d');if(!c)throw Error('Image export is unavailable');
 const mine=f.doubles?myName+' + '+(row[33]||'Partner'):myName,other=f.doubles?opponent+' + '+(row[35]||'Opponent 2'):opponent;
 c.fillStyle='#080d17';c.fillRect(0,0,1080,1280);c.textAlign='left';c.fillStyle='#edf4ff';c.font='700 46px system-ui';c.fillText('Keep Score',58,82);c.font='24px system-ui';c.fillStyle='#a7b6c9';c.fillText(sportName(f.sport)+' · '+(f.doubles?'Doubles':'Singles')+' · '+localDate(row),58,128);
 for(let i=0;i<2;i++){const x=i?554:54;c.fillStyle=i?'#b82e4c':'#1668cb';c.beginPath();c.roundRect(x,180,472,510,32);c.fill();c.fillStyle='#fff';label(c,i?other:mine,x+236,236,414,32,6);c.textAlign='center';c.font='750 180px system-ui';c.fillText(row[i?12:11],x+236,605);}
 c.fillStyle='#d6e8ff';c.font='600 29px system-ui';c.textAlign='center';c.fillText((f.nested?'Sets':'Games')+' · '+(row[23]?'Won by retirement':'Final result'),540,746);
 const scores=scorePairs(row).filter(Boolean);c.font='28px system-ui';scores.forEach(([a,b],i)=>{c.fillStyle='#142236';c.beginPath();c.roundRect(200,785+i*43,680,36,10);c.fill();c.fillStyle='#edf4ff';c.fillText((f.finalTie&&i===Number(row[6])-1?'Match tie-break':f.nested?'Set':'Game')+' '+(i+1)+'     '+a+' – '+b,540,813+i*43);});
 c.fillStyle='#a7b6c9';c.font='23px system-ui';c.fillText('Best of '+row[6]+(row[10]?' · '+Math.floor(Number(row[10])/60)+' min '+Number(row[10])%60+' sec':''),540,1160);c.fillText('Keep score. Keep playing.',540,1218);
 return await new Promise((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(Error('Could not create result image')),'image/png'));
}
export async function downloadResultCard(row,opponent,myName='Me'){
 const blob=await resultCardBlob(row,opponent,myName),file=datedFile(rowRules(row).sport+'_Result_'+row[2]+'.png'),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=file;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
export async function shareResultCard(row,opponent,myName='Me'){
 const blob=await resultCardBlob(row,opponent,myName),file=new File([blob],datedFile(rowRules(row).sport+'_Result.png'),{type:'image/png'});if(navigator.canShare?.({files:[file]}))await navigator.share({files:[file],title:'Keep Score · '+sportName(rowRules(row).sport)});else await downloadResultCard(row,opponent,myName);
}
