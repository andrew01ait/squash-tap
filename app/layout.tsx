import type { Metadata } from "next";
export const metadata:Metadata={title:"Keep Score — six racket sports on your Wear OS watch",description:"An offline Wear OS scorekeeper for squash, tennis, badminton, padel, table tennis and pickleball. Clear serving indicators, shared players and separate results. No account required."};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>;}
