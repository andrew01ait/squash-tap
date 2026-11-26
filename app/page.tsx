import landing from "../public/index.html?raw";

export default function Home() {
  const style = landing.match(/<style>([\s\S]*?)<\/style>/)?.[1] || "";
  const body = landing.match(/<body>([\s\S]*?)<\/body>/)?.[1] || "";
  const schema = landing.match(/<script type="application\/ld\+json">([^<]+)<\/script>/)?.[1] || "";
  return <>
    <script type="application/ld+json" dangerouslySetInnerHTML={{__html:schema}}/>
    <style dangerouslySetInnerHTML={{__html:style}}/>
    <div dangerouslySetInnerHTML={{__html:body}}/>
  </>;
}
