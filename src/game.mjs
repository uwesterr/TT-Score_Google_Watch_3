import { createState, queueDirection, step, togglePause } from './logic.mjs';

const canvas = document.getElementById('board');
const ctx = canvas.getContext('2d');
const scoreEl = document.getElementById('score');
const statusEl = document.getElementById('status');
const restartBtn = document.getElementById('restart');
const pauseBtn = document.getElementById('pause');
const controlButtons = document.querySelectorAll('[data-dir]');

const GRID_SIZE = 20; // cells across and down
const CELL = canvas.width / GRID_SIZE;
const TICK_MS = 140;

let state = createState(GRID_SIZE, GRID_SIZE);
let timerId = null;

function startLoop() {
  stopLoop();
  timerId = setInterval(() => {
    state = step(state);
    render();
  }, TICK_MS);
}

function stopLoop() {
  if (timerId) clearInterval(timerId);
}

function restart() {
  state = createState(GRID_SIZE, GRID_SIZE);
  startLoop();
  render();
}

function handleDirectionChange(dir) {
  state = queueDirection(state, dir);
}

function handleKeydown(evt) {
  const key = evt.key.toLowerCase();
  if (['arrowup', 'w'].includes(key)) handleDirectionChange('up');
  if (['arrowdown', 's'].includes(key)) handleDirectionChange('down');
  if (['arrowleft', 'a'].includes(key)) handleDirectionChange('left');
  if (['arrowright', 'd'].includes(key)) handleDirectionChange('right');
  if (key === ' ') {
    evt.preventDefault();
    toggle();
  }
}

function toggle() {
  state = togglePause(state);
  if (state.status === 'paused') {
    statusEl.textContent = 'Paused';
  } else {
    statusEl.textContent = 'Running';
  }
  render();
}

function renderGrid() {
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.strokeStyle = 'rgba(17, 24, 39, 0.06)';
  ctx.lineWidth = 1;
  for (let i = 0; i <= GRID_SIZE; i++) {
    const pos = i * CELL;
    ctx.beginPath();
    ctx.moveTo(pos, 0);
    ctx.lineTo(pos, canvas.height);
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(0, pos);
    ctx.lineTo(canvas.width, pos);
    ctx.stroke();
  }
}

function renderSnake() {
  ctx.fillStyle = '#111827';
  state.snake.forEach((part, idx) => {
    const padding = idx === 0 ? 2 : 4;
    ctx.fillRect(part.x * CELL + padding, part.y * CELL + padding, CELL - padding * 2, CELL - padding * 2);
  });
}

function renderFood() {
  if (!state.food) return;
  ctx.fillStyle = '#e11d48';
  const r = CELL / 2.4;
  ctx.beginPath();
  ctx.arc(state.food.x * CELL + CELL / 2, state.food.y * CELL + CELL / 2, r, 0, Math.PI * 2);
  ctx.fill();
}

function render() {
  renderGrid();
  renderFood();
  renderSnake();

  scoreEl.textContent = `Score: ${state.score}`;
  const label = state.status === 'dead' ? 'Game Over' : state.status.charAt(0).toUpperCase() + state.status.slice(1);
  statusEl.textContent = label;
  pauseBtn.textContent = state.status === 'paused' ? 'Resume' : 'Pause';

  if (state.status === 'dead') stopLoop();
}

restartBtn.addEventListener('click', restart);
pauseBtn.addEventListener('click', toggle);
window.addEventListener('keydown', handleKeydown);
controlButtons.forEach(btn => {
  btn.addEventListener('click', () => handleDirectionChange(btn.dataset.dir));
});

restart();
