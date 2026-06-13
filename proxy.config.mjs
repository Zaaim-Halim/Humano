const backendHost = '127.0.0.1';
const backendPort = 8080;

/**
 * @type {import('vite').CommonServerOptions['proxy']}
 */
export default {
  '/websocket': {
    target: `ws://${backendHost}:${backendPort}`,
    ws: true,
  },
  '^/(api|services|management|v3/api-docs)': {
    target: `http://${backendHost}:${backendPort}`,
    xfwd: true,
  },
  '/h2-console': {
    target: `http://${backendHost}:${backendPort}`,
    changeOrigin: true,
    secure: false,
  },
};
