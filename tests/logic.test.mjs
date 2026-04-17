import assert from 'assert/strict';
import { createState, step, queueDirection, placeFood } from '../src/logic.mjs';

const rngFixed = () => 0; // deterministic pick first available cell

function runSteps(state, dirs) {
  let s = state;
  for (const dir of dirs) {
    s = queueDirection(s, dir);
    s = step(s, rngFixed);
  }
  return s;
}

// Movement forward
{
  const state = createState(10, 10, rngFixed);
  const next = step(state, rngFixed);
  assert.equal(next.snake[0].x, state.snake[0].x + 1);
  assert.equal(next.snake[0].y, state.snake[0].y);
}

// Eating food grows snake and increases score
{
  let state = createState(5, 5, rngFixed);
  state = { ...state, food: { x: state.snake[0].x + 1, y: state.snake[0].y } };
  const next = step(state, rngFixed);
  assert.equal(next.snake.length, state.snake.length + 1);
  assert.equal(next.score, 1);
}

// Wall collision ends game
{
  let state = createState(3, 3, rngFixed);
  state = runSteps(state, ['right', 'right']);
  const hit = step(state, rngFixed);
  assert.equal(hit.status, 'dead');
}

// Self collision ends game
{
  const state = {
    ...createState(5, 5, rngFixed),
    snake: [
      { x: 2, y: 2 },
      { x: 2, y: 3 },
      { x: 1, y: 3 },
      { x: 1, y: 2 },
    ],
    direction: 'up',
    queued: 'left',
    food: { x: 4, y: 4 },
  };
  const hit = step(state, rngFixed); // head moves left into its body
  assert.equal(hit.status, 'dead');
}

// Food never spawns on snake and returns null when board full
{
  const snake = [];
  const width = 2, height = 2;
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) snake.push({ x, y });
  }
  assert.equal(placeFood(snake, width, height, rngFixed), null);
}

console.log('logic.test.mjs passed');
