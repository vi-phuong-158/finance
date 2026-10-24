import { registerHooks } from 'node:module';

registerHooks({
  resolve(specifier, context, nextResolve) {
    if (context.parentURL?.startsWith('file:') && specifier.startsWith('.') && /\.js$/i.test(specifier)) {
      try {
        return nextResolve(specifier.replace(/\.js$/i, '.ts'), context);
      } catch (error) {
        if (error?.code !== 'ERR_MODULE_NOT_FOUND') throw error;
      }
    }

    if (context.parentURL?.startsWith('file:') && specifier.startsWith('.') && !/\.[cm]?[jt]sx?$/i.test(specifier)) {
      try {
        return nextResolve(`${specifier}.ts`, context);
      } catch (error) {
        if (error?.code !== 'ERR_MODULE_NOT_FOUND') throw error;
      }
    }

    return nextResolve(specifier, context);
  },
});

