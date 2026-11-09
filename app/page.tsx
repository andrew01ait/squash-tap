import landing from "../public/index.html?raw";
export default function Home(){ const style=landing.match(/<style>([\s\S]*?)<\/style>/)?.[1]||"";const body=landing.match(/<body>([\s\S]*?)<\/body>/)?.[1]||"";return <><style dangerouslySetInnerHTML={{__html:style}}/><div dangerouslySetInnerHTML={{__html:body}}/></>; }
