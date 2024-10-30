import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';
import react from 'eslint-plugin-react';
import prettier from 'eslint-config-prettier'

export default tseslint.config(
  eslint.configs.recommended,
  ...tseslint.configs.recommended,
  react.configs.flat.recommended,
  prettier,
  {
    rules: {
      "@typescript-eslint/no-explicit-any": "off"
    },
    settings: {
      react: {
        version: 'detect'
      }
    }
  }
);
