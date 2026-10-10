// Descarga: redirige al APK del último release de GitHub
module.exports = async (req, res) => {
  res.writeHead(302, {
    Location: 'https://github.com/julianmcancelo/michitv-app/releases/latest/download/michitv.apk',
  });
  res.end();
};
