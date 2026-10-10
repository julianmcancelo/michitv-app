// Descarga: redirige al último release de GitHub (el APK pesa ~25MB,
// más de lo que una función serverless puede devolver).
module.exports = async (req, res) => {
  res.writeHead(302, {
    Location: 'https://github.com/julianmcancelo/michitv-app/releases/latest',
  });
  res.end();
};
