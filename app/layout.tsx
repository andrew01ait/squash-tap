import type { Metadata } from "next";
import landing from "../public/index.html?raw";

const origin = landing.match(/<link rel="canonical" href="([^"]+)"/)![1];
const title = landing.match(/<title>([^<]+)<\/title>/)![1];
const description = landing.match(/<meta name="description" content="([^"]+)"/)![1];
const image = landing.match(/<meta property="og:image" content="([^"]+)"/)![1];
const alt = landing.match(/<meta property="og:image:alt" content="([^"]+)"/)![1];

export const metadata: Metadata = {
  metadataBase: new URL(origin), title, description, icons: {icon: "/favicon.svg"},
  alternates: {canonical: origin},
  robots: {index: true, follow: true, "max-image-preview": "large"},
  openGraph: {type: "website", siteName: "Keep Score", title, description, url: origin, images: [{url:image, width:1200, height:630, alt}]},
  twitter: {card: "summary_large_image", title, description, images: [{url:image, alt}]}
};

export default function RootLayout({children}: {children: React.ReactNode}) {
  return <html lang="en"><body>{children}</body></html>;
}
