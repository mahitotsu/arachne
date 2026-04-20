import type { NextConfig } from 'next';

const backendOrigin = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080';

const nextConfig: NextConfig = {
  output: 'standalone',
  async rewrites() {
    return [
      {
        source: '/api/backend/:path*',
        destination: `${backendOrigin}/api/:path*`
      }
    ];
  }
};

export default nextConfig;