import type { NextConfig } from 'next';

const backendOrigin = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080';
const customerServiceOrigin = process.env.CUSTOMER_SERVICE_ORIGIN ?? 'http://localhost:8085';
const menuServiceOrigin = process.env.MENU_SERVICE_ORIGIN ?? 'http://localhost:8081';

const nextConfig: NextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/customer/:path*',
        destination: `${customerServiceOrigin}/api/:path*`
      },
      {
        source: '/api/menu/:path*',
        destination: `${menuServiceOrigin}/api/menu/:path*`
      },
      {
        source: '/api/backend/:path*',
        destination: `${backendOrigin}/api/:path*`
      }
    ];
  }
};

export default nextConfig;