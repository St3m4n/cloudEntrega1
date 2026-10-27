"""Deploy the prepared lab stack through SSM. Secrets stay in ignored state/SecureString."""
import argparse
import base64
import gzip
import json
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root / 'tmp/awsdeps'))
import boto3
from botocore.config import Config

parser = argparse.ArgumentParser()
parser.add_argument('action', choices=['status', 'prepare', 'deploy', 'command', 'result'])
parser.add_argument('--service')
parser.add_argument('--command')
parser.add_argument('--command-id')
parser.add_argument('--brokers-only', action='store_true', help='Prepare broker hosts while RDS is still provisioning')
args = parser.parse_args()
state_path = root / 'tmp/lab/runtime.json'
state = json.loads(state_path.read_text(encoding='utf-8'))
session = boto3.Session(profile_name=state['profile'], region_name=state['region'])
config = Config(connect_timeout=10, read_timeout=25, retries={'max_attempts': 2})
cf = session.client('cloudformation', config=config)
ssm = session.client('ssm', config=config)
service_to_resource = {name: 'Ec2Apps' for name in ('apps','bff','catalog','orders','audit','report','notify','rabbit-admin','kafka-admin')}
service_to_resource.update({'mq':'Ec2Mq','kafka':'Ec2Kafka'})

if args.action == 'status':
    stack = cf.describe_stacks(StackName=state['stackName'])['Stacks'][0]
    events = cf.describe_stack_events(StackName=state['stackName'])['StackEvents']
    print(json.dumps({'status': stack['StackStatus'], 'recent': [
        {'resource': event['LogicalResourceId'], 'status': event['ResourceStatus'],
         'reason': event.get('ResourceStatusReason')} for event in events[:8]]}, indent=2))
elif args.action == 'prepare':
    stack = cf.describe_stacks(StackName=state['stackName'])['Stacks'][0]
    ready = stack['StackStatus'] in ('CREATE_COMPLETE', 'UPDATE_COMPLETE')
    if not ready and not (args.brokers_only and stack['StackStatus'] == 'CREATE_IN_PROGRESS'):
        raise RuntimeError('Stack not ready: ' + stack['StackStatus'])
    state['outputs'] = {item['OutputKey']: item['OutputValue'] for item in stack.get('Outputs', [])}
    resources = cf.list_stack_resources(StackName=state['stackName'])['StackResourceSummaries']
    state['instances'] = {name: next(item['PhysicalResourceId'] for item in resources
                                    if item['LogicalResourceId'] == resource)
                          for name, resource in service_to_resource.items()}
    state['accountId'] = session.client('sts', config=config).get_caller_identity()['Account']
    if not ready:
        instances = session.client('ec2', config=config).describe_instances(InstanceIds=list(set(state['instances'].values())))
        by_id = {item['InstanceId']: item for reservation in instances['Reservations'] for item in reservation['Instances']}
        for name in ('apps', 'mq', 'kafka'):
            state['outputs'][service_to_resource[name] + 'PrivateIp'] = by_id[state['instances'][name]]['PrivateIpAddress']
    state.setdefault('imageTag', 'mvp-20261008')
    state_path.write_text(json.dumps(state, indent=2) + '\n', encoding='utf-8')
    ssm.put_parameter(Name='/pedidos360/lab/runtime', Type='SecureString', Value=json.dumps(state), Overwrite=True,
                      Description='Temporary Pedidos360 lab runtime; contains application secrets')
    if ready:
        frontend_env = root / 'frontend-pedidos360/.env.local'
        contents = frontend_env.read_text(encoding='utf-8')
        lines = [line for line in contents.splitlines() if not line.startswith('VITE_BFF_BASE_URL=')]
        frontend_env.write_text('\n'.join(lines) + '\nVITE_BFF_BASE_URL=' + state['outputs']['ApiUrl'] + '\n', encoding='utf-8')
    print(json.dumps({'ready': ready, 'apiUrl': state['outputs'].get('ApiUrl'), 'instances': state['instances']}, indent=2))
elif args.action in ('deploy', 'command'):
    service = args.service
    instance = state['instances']['apps' if service == 'init-db' else service]
    if args.action == 'command':
        commands = [args.command]
    else:
        files = {'lab_remote.py': (root / 'scripts/lab_remote.py').read_text(encoding='utf-8')}
        if service in ('mq', 'kafka'):
            files['compose.yml'] = (root / ('infra/' + service + '/compose.yml')).read_text(encoding='utf-8')
        elif service != 'init-db':
            if service != 'apps':
                raise ValueError('Deploy apps as one Compose; individual services share ec2-apps.')
            compose = json.loads((root / 'infra/apps/compose.yml').read_text(encoding='utf-8'))
            files['compose.yml'] = json.dumps(compose, indent=2)
        payload = base64.b64encode(gzip.compress(json.dumps(files).encode())).decode()
        stage = "import base64,gzip,json,pathlib; p=pathlib.Path('/opt/pedidos360/" + service + "'); p.mkdir(parents=True,exist_ok=True); "
        stage += "files=json.loads(gzip.decompress(base64.b64decode('" + payload + "'))); "
        stage += "[(p/name).write_text(contents) for name,contents in files.items()]"
        commands = ['set -eu', 'cloud-init status --wait', "python3 - <<'P360PY'\n" + stage + '\nP360PY']
        if service == 'init-db':
            commands += ['dnf install -y python3-pip', 'python3 -m venv /opt/pedidos360/venv',
                         '/opt/pedidos360/venv/bin/pip install oracledb',
                         '/opt/pedidos360/venv/bin/python /opt/pedidos360/init-db/lab_remote.py init-db']
        else:
            commands += ['python3 /opt/pedidos360/' + service + '/lab_remote.py ' + service]
    response = ssm.send_command(InstanceIds=[instance], DocumentName='AWS-RunShellScript',
                                Comment='Pedidos360 ' + args.action + ' ' + service,
                                Parameters={'commands': commands, 'executionTimeout': ['1800']}, TimeoutSeconds=1800)
    print(json.dumps({'service': service, 'instance': instance, 'commandId': response['Command']['CommandId']}))
elif args.action == 'result':
    service = args.service
    instance = state['instances']['apps' if service == 'init-db' else service]
    result = ssm.get_command_invocation(CommandId=args.command_id, InstanceId=instance)
    print(json.dumps({key: result.get(key) for key in ('Status', 'ResponseCode', 'StandardOutputContent', 'StandardErrorContent')}, indent=2))
