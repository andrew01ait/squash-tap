// An older cached referee page cannot read a device invitation. Open the current
// offline shell before passing it the QR fragment. The fragment stays on-device.
async function updateShell(){
  if(!('serviceWorker' in navigator))return;
  const registration=await navigator.serviceWorker.register('/referee-sw.js',{scope:'/referee',type:'module',updateViaCache:'none'});
  await registration.update();
  const worker=registration.installing||registration.waiting;
  if(!worker)return;
  await new Promise(resolve=>{
    const finished=()=>{
      if(worker.state==='activated'||worker.state==='redundant'){
        worker.removeEventListener('statechange',finished);
        resolve();
      }
    };
    worker.addEventListener('statechange',finished);
    finished();
  });
}
try{await updateShell();}catch{/* The online console can also run without a service worker. */}
location.replace('/referee.html'+location.hash);
