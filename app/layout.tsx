import type { Metadata } from "next";
export const metadata:Metadata={title:"Squash Tap — squash scores on your Wear OS watch",description:"Keep squash points, sets, opponents and match results on your Wear OS watch."};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="en"><body>{children}</body></html>;}
