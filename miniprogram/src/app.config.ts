export default defineAppConfig({
  pages: [
    'pages/index/index',
    'pages/room/index',
    'pages/game/index'
  ],
  window: {
    backgroundTextStyle: 'dark',
    navigationBarBackgroundColor: '#0c3b2a',
    navigationBarTitleText: '南陵麻将',
    navigationBarTextStyle: 'white',
    backgroundColor: '#0c3b2a'
  },
  style: 'v2',
  sitemapLocation: 'sitemap.json'
})
