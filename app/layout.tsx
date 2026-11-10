import type { Metadata } from "next";
export const metadata:Metadata={title:"Squash Tap — five racket sports on your Wear OS watch",description:"An offline Wear OS scorekeeper for squash, tennis, badminton, padel and table tennis. Clear serving indicators, shared players and separate results. No account required."};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>;}
