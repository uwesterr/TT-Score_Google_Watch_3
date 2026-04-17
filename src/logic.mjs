const DIRECTIONS = {
  up: { x: 0, y: -1 },
  down: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
};

const OPPOSITE = { up: 'down', down: 'up', left: 'right', right: 'left' };

export function createState(width, height, rng = Math.random) {
  const start = { x: Math.floor(width / 2), y: Math.floor(height / 2) };
  const snake = [start];
  return {
    width,
    height,
    snake,
    direction: 'right',
    queued: 'right',
    food: placeFood(snake, width, height, rng),
    score: 0,
    status: 'running', // running | paused | dead
  };
}

export function placeFood(snake, width, height, rng = Math.random) {
  const open = [];
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      if (!snake.some(part => part.x === x && part.y === y)) {
        open.push({ x, y });
      }
    }
  }
  if (open.length === 0) return null;
  const pick = open[Math.floor(rng() * open.length)];
  return pick;
}

export function queueDirection(state, nextDir) {
  if (!DIRECTIONS[nextDir]) return state;
  if (OPPOSITE[state.direction] === nextDir) return state;
  return { ...state, queued: nextDir };
}

export function step(state, rng = Math.random) {
  if (state.status !== 'running') return state;

  const dir = DIRECTIONS[state.queued] || DIRECTIONS[state.direction];
  const head = state.snake[0];
  const next = { x: head.x + dir.x, y: head.y + dir.y };

  // Wall collision
  if (next.x < 0 || next.x >= state.width || next.y < 0 || next.y >= state.height) {
    return { ...state, status: 'dead' };
  }

  // Self collision
  if (state.snake.some(part => part.x === next.x && part.y === next.y)) {
    return { ...state, status: 'dead' };
  }

  const newSnake = [next, ...state.snake];
  let newFood = state.food;
  let newScore = state.score;

  if (state.food && next.x === state.food.x && next.y === state.food.y) {
    newScore += 1;
    newFood = placeFood(newSnake, state.width, state.height, rng);
  } else {
    newSnake.pop();
  }

  return {
    ...state,
    snake: newSnake,
    direction: state.queued,
    food: newFood,
    score: newScore,
  };
}

export function togglePause(state) {
  if (state.status === 'dead') return state;
  const nextStatus = state.status === 'paused' ? 'running' : 'paused';
  return { ...state, status: nextStatus };
}

export function isOccupying(snake, cell) {
  return snake.some(part => part.x === cell.x && part.y === cell.y);
}

export { DIRECTIONS };
