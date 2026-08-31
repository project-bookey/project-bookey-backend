import type { Metadata } from 'next';

import './globals.css';
import { Providers } from '@/components/Providers';

export const metadata: Metadata = {
  title: 'bookey 관리자',
  description: 'bookey 운영 백오피스',
  // 관리자 화면은 검색엔진에 노출되지 않는다 (§F13).
  robots: { index: false, follow: false, nocache: true },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
