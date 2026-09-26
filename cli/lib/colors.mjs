// Zero-dependency terminal colors and formatting

const isColorSupported = !process.env.NO_COLOR && (process.stdout.isTTY || process.env.FORCE_COLOR);

function color(start, end) {
  return (text) => isColorSupported ? `\x1b[${start}m${text}\x1b[${end}m` : String(text);
}

export const c = {
  bold: color(1, 22),
  dim: color(2, 22),
  italic: color(3, 23),
  underline: color(4, 24),
  inverse: color(7, 27),
  
  black: color(30, 39),
  red: color(31, 39),
  green: color(32, 39),
  yellow: color(33, 39),
  blue: color(34, 39),
  magenta: color(35, 39),
  cyan: color(36, 39),
  white: color(37, 39),
  gray: color(90, 39),
  
  bgBlack: color(40, 49),
  bgRed: color(41, 49),
  bgGreen: color(42, 49),
  bgYellow: color(43, 49),
  bgBlue: color(44, 49),
  bgMagenta: color(45, 49),
  bgCyan: color(46, 49),
  bgWhite: color(47, 49),
};
