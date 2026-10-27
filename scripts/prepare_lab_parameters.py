"""Prepare ignored deployment state without printing passwords or AWS credentials."""
import json
import secrets
import string
import argparse
from pathlib import Path

root = Path(__file__).resolve().parents[1]
private_dir = root / 'tmp/lab'
private_dir.mkdir(parents=True, exist_ok=True)
parser = argparse.ArgumentParser()
parser.add_argument('--inputs', default='tmp/lab/inputs.json', help='Non-secret account, network and tenant configuration')
args = parser.parse_args()
inputs = json.loads((root / args.inputs).read_text(encoding='utf-8'))
state_path = private_dir / 'runtime.json'
state = json.loads(state_path.read_text(encoding='utf-8')) if state_path.exists() else {}
alphabet = string.ascii_letters + string.digits
state.setdefault('dbMasterPassword', ''.join(secrets.choice(alphabet) for _ in range(24)))
state.setdefault('rabbitPassword', ''.join(secrets.choice(alphabet) for _ in range(32)))
state.setdefault('rabbitCookie', ''.join(secrets.choice(alphabet) for _ in range(48)))
state.setdefault('schemaPasswords', {name: ''.join(secrets.choice(alphabet) for _ in range(24))
                                    for name in ('CATALOG', 'ORDERS', 'AUDIT', 'REPORT')})
if len(state['dbMasterPassword']) > 30 or any(len(password) > 30 for password in state['schemaPasswords'].values()):
    raise ValueError('Oracle passwords must be at most 30 characters; update undeployed runtime state explicitly.')
state.update({'region': inputs['aws']['region'], 'profile': inputs['aws']['profile'],
              'stackName': inputs['lab']['stackName'], 'imageTag': inputs['lab']['imageTag'],
              'tenantId': inputs['entra']['tenantId'], 'spaClientId': inputs['entra']['spaClientId'],
              'apiClientId': inputs['entra']['apiClientId']})
state_path.write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')
values = {
    'VpcId': inputs['network']['vpcId'], 'PublicSubnet': inputs['network']['publicSubnetId'],
    'PrivateAzA': inputs['network']['privateAzA'], 'PrivateAzB': inputs['network']['privateAzB'],
    'PrivateCidrA': inputs['network']['privateCidrA'], 'PrivateCidrB': inputs['network']['privateCidrB'],
    'InstanceProfileName': inputs['aws']['existingInstanceProfile'], 'DbMasterPassword': state['dbMasterPassword'],
    'EntraIssuer': inputs['entra']['issuer'],
    'EntraAudience': inputs['entra']['audience'], 'FrontendOrigin': inputs['frontend']['origin'],
}
params = [{'ParameterKey': key, 'ParameterValue': value} for key, value in values.items()]
(private_dir / 'parameters.json').write_text(json.dumps(params, indent=2) + '\n', encoding='utf-8')
frontend_env = root / 'frontend-pedidos360/.env.local'
frontend_env.write_text('VITE_ENTRA_TENANT_ID=' + state['tenantId'] + '\n'
                        'VITE_SPA_CLIENT_ID=' + state['spaClientId'] + '\n'
                        'VITE_API_CLIENT_ID=' + state['apiClientId'] + '\n'
                        'VITE_BFF_BASE_URL=http://localhost:8080\n', encoding='utf-8')
print('Parameters and runtime state saved under ignored tmp/lab; frontend tenant configured.')
