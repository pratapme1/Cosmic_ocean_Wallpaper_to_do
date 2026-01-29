const { generateBackgroundLayer } = require('./services/wallpaper-generator-enhanced');

const colors = {
    bgPrimary: '#000814',
    bgSecondary: '#001D3D'
};

const width = 1080;
const height = 1920;
const breathScale = 0.92; // Worst case scale from animation-system
const offsetX = (width - width * breathScale) / 2;
const offsetY = (height - height * breathScale) / 2;

const svg = generateBackgroundLayer(width, height, colors, breathScale, offsetX, offsetY);
console.log(svg);
