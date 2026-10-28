const clock = new Intl.DateTimeFormat('en-GB', {
  hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
});
const tickNumber = new Intl.NumberFormat('en-US', { maximumFractionDigits: 3 });

export function axisMaximum(values) {
  const maximum = Math.max(0, ...values);
  if (maximum === 0) return 1;
  const padded = maximum * 1.1;
  const magnitude = 10 ** Math.floor(Math.log10(padded));
  const step = [1, 2, 5, 10].find(value => value >= padded / magnitude) ?? 10;
  return step * magnitude;
}

export function timeLabels(first, last, width, origin = performance.timeOrigin) {
  if (last === first) return [{ fraction: 0, label: clock.format(new Date(origin + first)) }];
  const count = Math.max(2, Math.min(6, Math.floor(width / 105), Math.floor((last - first) / 1000) + 1));
  const labels = Array.from({ length: count }, (_, index) => {
    const fraction = index / (count - 1);
    return { fraction, label: clock.format(new Date(origin + first + (last - first) * fraction)) };
  });
  return labels.filter((tick, index) => index === 0 || tick.label !== labels[index - 1].label);
}

function prepare(canvas) {
  const context = canvas.getContext('2d');
  if (!context) return null;
  const width = canvas.clientWidth || canvas.width;
  const height = canvas.clientHeight || canvas.height;
  const scale = globalThis.devicePixelRatio || 1;
  canvas.width = Math.round(width * scale);
  canvas.height = Math.round(height * scale);
  context.setTransform(scale, 0, 0, scale, 0, 0);
  context.clearRect(0, 0, width, height);
  return { context, width, height };
}

function tickText(value) {
  return value > 0 && value < 0.001 ? value.toPrecision(2) : tickNumber.format(value);
}

function grid(context, plot, maximum, ticks, hasValues) {
  context.lineWidth = 1;
  context.font = '11px system-ui';
  context.fillStyle = '#59677c';
  context.textBaseline = 'middle';
  for (let index = 0; index <= 4; index++) {
    const y = plot.bottom - index / 4 * plot.height;
    context.strokeStyle = '#eaf0f5';
    context.beginPath(); context.moveTo(plot.left, y); context.lineTo(plot.right, y); context.stroke();
    if (hasValues) {
      context.textAlign = 'right';
      context.fillText(tickText(maximum * index / 4), plot.left - 10, y);
    }
  }
  context.textBaseline = 'top';
  for (const tick of ticks) {
    const x = plot.left + tick.fraction * plot.width;
    context.strokeStyle = '#f0f3f7';
    context.beginPath(); context.moveTo(x, plot.top); context.lineTo(x, plot.bottom); context.stroke();
    context.textAlign = 'center';
    if (tick.fraction === 0) context.textAlign = 'left';
    if (tick.fraction === 1) context.textAlign = 'right';
    context.fillText(tick.label, x, plot.bottom + 10);
  }
}

function segments(samples, field, plot, maximum) {
  const first = samples[0].time;
  const duration = Math.max(samples.at(-1).time - first, 1);
  const result = [];
  let current = [];
  for (const sample of samples) {
    const value = sample[field];
    if (!Number.isFinite(value) || value < 0) {
      if (current.length) result.push(current);
      current = [];
    } else {
      current.push({ x: plot.left + (sample.time - first) / duration * plot.width,
        y: plot.bottom - value / maximum * plot.height });
    }
  }
  if (current.length) result.push(current);
  return result;
}

function series(context, points, baseline, color, fill) {
  if (points.length > 1) {
    context.beginPath(); context.moveTo(points[0].x, baseline);
    for (const point of points) context.lineTo(point.x, point.y);
    context.lineTo(points.at(-1).x, baseline); context.closePath();
    context.fillStyle = fill; context.fill();
    context.beginPath(); context.moveTo(points[0].x, points[0].y);
    for (const point of points.slice(1)) context.lineTo(point.x, point.y);
    context.strokeStyle = color; context.lineWidth = 1.8; context.stroke();
  }
  context.fillStyle = color;
  for (const point of points) {
    context.beginPath(); context.arc(point.x, point.y, 2.2, 0, Math.PI * 2); context.fill();
  }
}

export function drawChart(canvas, samples, field) {
  const surface = prepare(canvas);
  if (!surface) return;
  const { context, width, height } = surface;
  const plot = { left: 45, right: width - 12, top: 12, bottom: height - 30,
    width: width - 57, height: height - 42 };
  const values = samples.map(sample => sample[field]).filter(value => Number.isFinite(value) && value >= 0);
  const maximum = axisMaximum(values);
  const ticks = samples.length ? timeLabels(samples[0].time, samples.at(-1).time, plot.width) : [];
  grid(context, plot, maximum, ticks, values.length > 0);
  if (!values.length) {
    context.textAlign = 'center'; context.textBaseline = 'middle'; context.font = '13px system-ui';
    context.fillText(field === 'meanMs' ? 'No invocation latency samples' : 'Waiting for polling samples',
      plot.left + plot.width / 2, plot.top + plot.height / 2);
    return;
  }
  const color = field === 'rate' ? '#07834c' : '#6938c4';
  const fill = field === 'rate' ? 'rgba(7, 131, 76, 0.055)' : 'rgba(105, 56, 196, 0.04)';
  for (const points of segments(samples, field, plot, maximum)) series(context, points, plot.bottom, color, fill);
}
