# Contributing to AstralStream

First off, thank you for considering contributing to AstralStream! It's people like you that make AstralStream such a great tool.

## Code of Conduct

By participating in this project, you are expected to uphold our Code of Conduct:
- Be respectful and inclusive
- Welcome newcomers and help them get started
- Focus on constructive criticism
- Show empathy towards other community members

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When creating a bug report, include:

- **Clear title and description**
- **Steps to reproduce**
- **Expected behavior**
- **Actual behavior**
- **Screenshots** (if applicable)
- **System information** (OS, version, etc.)

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

- **Clear title and description**
- **Use case** - explain why this enhancement would be useful
- **Possible implementation** - if you have ideas on how to implement it
- **Alternatives considered** - mention any alternative solutions you've thought about

### Pull Requests

1. **Fork the repo** and create your branch from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes**:
   - Follow the existing code style
   - Add tests for new functionality
   - Update documentation as needed

3. **Test your changes**:
   ```bash
   pnpm test
   pnpm lint
   pnpm type-check
   ```

4. **Commit your changes** using conventional commits:
   ```bash
   git commit -m "feat: add amazing feature"
   ```

5. **Push to your fork** and submit a pull request

## Development Setup

### Prerequisites

- Node.js 18+
- pnpm 8+
- Flutter SDK 3.0+ (for mobile development)
- Docker (for backend services)

### Getting Started

1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/AstralStream.git
   cd AstralStream
   ```

2. **Install dependencies**:
   ```bash
   pnpm install
   ```

3. **Set up environment variables**:
   ```bash
   cp .env.example .env.local
   ```

4. **Start development servers**:
   ```bash
   pnpm dev
   ```

### Project Structure

- `apps/` - Platform-specific applications
- `packages/` - Shared packages
- `services/` - Backend microservices
- `plugins/` - Plugin system
- `docs/` - Documentation

### Working with Different Platforms

#### Mobile (Flutter)
```bash
cd apps/mobile
flutter pub get
flutter run
```

#### Desktop
```bash
cd apps/desktop
pnpm dev
```

#### Web
```bash
cd apps/web
pnpm dev
```

#### Backend Services
```bash
docker-compose up
```

## Coding Guidelines

### TypeScript/JavaScript

- Use TypeScript for all new code
- Follow the existing ESLint configuration
- Use functional components and hooks in React
- Prefer composition over inheritance

### Flutter/Dart

- Follow the official [Dart style guide](https://dart.dev/guides/language/effective-dart/style)
- Use Riverpod for state management
- Keep widgets small and focused
- Write widget tests for UI components

### Git Workflow

1. **Branch naming**:
   - `feature/` - new features
   - `fix/` - bug fixes
   - `docs/` - documentation updates
   - `refactor/` - code refactoring
   - `test/` - test additions or fixes

2. **Commit messages** - Follow [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat:` - new feature
   - `fix:` - bug fix
   - `docs:` - documentation changes
   - `style:` - formatting changes
   - `refactor:` - code refactoring
   - `test:` - test changes
   - `chore:` - maintenance tasks

### Testing

- Write unit tests for business logic
- Write integration tests for API endpoints
- Write widget tests for UI components
- Maintain test coverage above 80%

### Documentation

- Update README.md for user-facing changes
- Add JSDoc comments for public APIs
- Update API documentation for endpoint changes
- Include inline comments for complex logic

## Plugin Development

See our [Plugin Development Guide](docs/guides/plugin-development.md) for information on creating AstralStream plugins.

## API Development

When adding new API endpoints:

1. Define OpenAPI schema
2. Implement endpoint with proper validation
3. Add tests
4. Update API documentation

## Performance Guidelines

- Lazy load components when possible
- Optimize images and assets
- Use memoization for expensive computations
- Profile performance regularly

## Security Guidelines

- Never commit sensitive data
- Validate all user inputs
- Use parameterized queries
- Keep dependencies updated
- Report security issues privately to security@astralstream.app

## Release Process

1. Create a release branch
2. Update version numbers
3. Update CHANGELOG.md
4. Create pull request to main
5. After merge, tag the release
6. Deploy to production

## Getting Help

- Join our [Discord server](https://discord.gg/astralstream)
- Check the [documentation](docs/)
- Ask questions in [GitHub Discussions](https://github.com/yourusername/AstralStream/discussions)

## Recognition

Contributors will be recognized in:
- README.md contributors section
- Release notes
- Project website

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

Thank you for contributing to AstralStream! 🌟