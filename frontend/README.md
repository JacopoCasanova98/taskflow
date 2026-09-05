# TaskFlow frontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.1.7.

Run commands from `frontend/`. Use a Node.js version supported by Angular 22
(bootstrap verified with Node 22.23.1 and npm 10.9.8), then install dependencies:

```bash
npm ci
```

MS3.1 uses standalone APIs, routing, strict checking, SCSS, and Vitest.
The application renders in the browser only; the empty Dockerfile is reserved for a later MacroStep.

## Development server

To start a local development server, run:

```bash
npm start
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

For API development, run the Spring Boot backend at `http://localhost:8080`.
Frontend code uses the relative `/api` base path. The default development server
uses `proxy.conf.json` to forward `/api/**` to the backend, preserving the path
without requiring development-only backend CORS configuration. Restart the
development server after changing proxy configuration.

Both `src/environments/environment.ts` (production/default) and
`environment.development.ts` use `/api`. Angular replaces the default file for
development builds. Root `app.config.ts` provides this value through `API_BASE_URL`;
future API clients inject the token rather than importing environment files.
Environment values are bundled into client code and must never contain secrets.
The development proxy is not included in production builds; production hosting
must route `/api` requests to the backend on the same origin.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
npm exec ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
npm exec ng generate -- --help
```

## Building

To build the project run:

```bash
npm run build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
npm test -- --watch=false
```

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
