import type { Metadata } from "next";
export const metadata:Metadata={title:"Keep Score — six racket sports on your Wear OS watch",description:"Keep Score for Wear OS: squash, tennis, badminton, padel, table tennis and pickleball. Offline scoring, saved results and optional live result streaming."};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>;}
