import type { NextConfig } from 'next';

const backendOrigin = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080';
const customerServiceOrigin = process.env.CUSTOMER_SERVICE_ORIGIN ?? 'http://localhost:8085';
const menuServiceOrigin = process.env.MENU_SERVICE_ORIGIN ?? 'http://localhost:8081';
const supportServiceOrigin = process.env.SUPPORT_SERVICE_ORIGIN ?? 'http://localhost:8086';
const registryServiceOrigin = process.env.REGISTRY_SERVICE_ORIGIN ?? 'http://localhost:8087';

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
      },
      {
        source: '/api/support/:path*',
        destination: `${supportServiceOrigin}/api/support/:path*`
      },
      {
        source: '/api/registry/:path*',
        destination: `${registryServiceOrigin}/registry/:path*`
      }
    ];
  }
};

export default nextConfig;