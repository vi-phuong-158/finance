import { checkDeployment } from '../lib/deployment-check.js';

checkDeployment(process.env.WEBHOOK_URL ?? '', process.env.WEBHOOK_SECRET ?? '').then(
  (result) => console.log(JSON.stringify(result)),
  () => { console.error('DEPLOYMENT_CHECK_FAILED'); process.exitCode = 1; },
);
