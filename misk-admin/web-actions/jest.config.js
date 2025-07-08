/** @type {import('ts-jest').JestConfigWithTsJest} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  moduleNameMapper: {
    "^src/(.*)$": "<rootDir>/src/$1",
    '^@web-actions/(.*)$': '<rootDir>/src/web-actions/$1'
  }
};
