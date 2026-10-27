"""Generate the Learner Lab variant; does not make AWS calls or contain secrets."""
import copy
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
template = json.loads((root / 'infra/aws/cloudformation.json').read_text(encoding='utf-8'))
template['Description'] = 'Pedidos360 entrega 2: three EC2 (apps, RabbitMQ, Kafka), existing lab profile, Oracle and JWT API. Billable.'
p = template['Parameters']
del p['PrivateSubnets']
p.update({
    'PublicSubnet': {'Type': 'AWS::EC2::Subnet::Id', 'Description': 'Existing public subnet in the VPC with an Internet Gateway route'},
    'PrivateAzA': {'Type': 'AWS::EC2::AvailabilityZone::Name'},
    'PrivateAzB': {'Type': 'AWS::EC2::AvailabilityZone::Name'},
    'PrivateCidrA': {'Type': 'String', 'Description': 'Unused CIDR within the existing VPC'},
    'PrivateCidrB': {'Type': 'String', 'Description': 'Unused CIDR within the existing VPC'},
    'InstanceProfileName': {'Type': 'String', 'Default': 'LabInstanceProfile'},
    'DbMasterPassword': {'Type': 'String', 'NoEcho': True, 'MinLength': 16, 'MaxLength': 30},
})
r = template['Resources']
del r['Ec2Role']
del r['Ec2Profile']
private_subnets = [{'Ref': 'PrivateSubnetA'}, {'Ref': 'PrivateSubnetB'}]

def replace_subnets(value):
    if isinstance(value, dict):
        if value == {'Ref': 'PrivateSubnets'}:
            return copy.deepcopy(private_subnets)
        return {key: replace_subnets(item) for key, item in value.items()}
    if isinstance(value, list):
        return [replace_subnets(item) for item in value]
    return value

template = replace_subnets(template)
r = template['Resources']
for suffix in ('A', 'B'):
    r['PrivateSubnet' + suffix] = {
        'Type': 'AWS::EC2::Subnet',
        'Properties': {'VpcId': {'Ref': 'VpcId'}, 'CidrBlock': {'Ref': 'PrivateCidr' + suffix},
                       'AvailabilityZone': {'Ref': 'PrivateAz' + suffix}, 'MapPublicIpOnLaunch': False,
                       'Tags': [{'Key': 'Name', 'Value': 'pedidos360-private-' + suffix.lower()}]},
    }
    r['PrivateAssociation' + suffix] = {
        'Type': 'AWS::EC2::SubnetRouteTableAssociation',
        'Properties': {'SubnetId': {'Ref': 'PrivateSubnet' + suffix}, 'RouteTableId': {'Ref': 'PrivateRoutes'}},
    }
r['NatAddress'] = {'Type': 'AWS::EC2::EIP', 'Properties': {'Domain': 'vpc'}}
r['Nat'] = {'Type': 'AWS::EC2::NatGateway', 'Properties': {
    'SubnetId': {'Ref': 'PublicSubnet'}, 'AllocationId': {'Fn::GetAtt': ['NatAddress', 'AllocationId']},
    'Tags': [{'Key': 'Name', 'Value': 'pedidos360-nat'}],
}}
r['PrivateRoutes'] = {'Type': 'AWS::EC2::RouteTable', 'Properties': {'VpcId': {'Ref': 'VpcId'}}}
r['PrivateDefaultRoute'] = {'Type': 'AWS::EC2::Route', 'Properties': {
    'RouteTableId': {'Ref': 'PrivateRoutes'}, 'DestinationCidrBlock': '0.0.0.0/0', 'NatGatewayId': {'Ref': 'Nat'},
}}
for resource in r.values():
    if resource['Type'] == 'AWS::EC2::Instance':
        resource['DependsOn'] = ['PrivateDefaultRoute', 'PrivateAssociationA']
        resource['Properties']['IamInstanceProfile'] = {'Ref': 'InstanceProfileName'}
        resource['Properties']['CreditSpecification'] = {'CPUCredits': 'standard'}
        if resource['Properties']['InstanceType'] == {'Ref': 'AppInstanceType'}:
            resource['Properties']['BlockDeviceMappings'][0]['Ebs']['VolumeSize'] = 20
db = r['Oracle']
db['DeletionPolicy'] = 'Delete'
db['UpdateReplacePolicy'] = 'Delete'
db['Properties'].pop('ManageMasterUserPassword')
db['Properties']['MasterUserPassword'] = {'Ref': 'DbMasterPassword'}
db['Properties']['BackupRetentionPeriod'] = 1
db['Properties']['DeletionProtection'] = False
template['Outputs'].pop('MasterSecretArn')
template['Outputs']['PrivateSubnetIds'] = {'Value': {'Fn::Join': [',', private_subnets]}}
destination = root / 'infra/aws/cloudformation-lab.json'
destination.write_text(json.dumps(template, indent=2) + '\n', encoding='utf-8')
print(destination.relative_to(root))
