import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "FokalPoint | Find the right creator. Bring your vision to life.",
  description: "Discover photographers, videographers and creative professionals. Connect, collaborate and book with confidence.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
