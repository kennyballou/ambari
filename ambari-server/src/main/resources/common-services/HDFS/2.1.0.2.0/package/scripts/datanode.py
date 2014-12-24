"""
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

"""
import datanode_upgrade
from hdfs_datanode import datanode
from resource_management import *
from resource_management.libraries.functions.version import compare_versions, \
  format_hdp_stack_version
from resource_management.libraries.functions.security_commons import build_expectations, \
  cached_kinit_executor, get_params_from_filesystem, validate_security_config_properties
from hdfs import hdfs


class DataNode(Script):
  def install(self, env):
    import params

    self.install_packages(env, params.exclude_packages)
    env.set_params(params)


  def pre_rolling_restart(self, env):
    Logger.info("Executing DataNode Rolling Upgrade pre-restart")
    import params
    env.set_params(params)

    if params.version and compare_versions(format_hdp_stack_version(params.version), '2.2.0.0') >= 0:
      Execute(format("hdp-select set hadoop-hdfs-datanode {version}"))


  def post_rolling_restart(self, env):
    Logger.info("Executing DataNode Rolling Upgrade post-restart")
    import params
    env.set_params(params)

    # ensure the DataNode has started and rejoined the cluster
    datanode_upgrade.post_upgrade_check()


  def start(self, env, rolling_restart=False):
    import params

    env.set_params(params)
    self.configure(env)
    datanode(action="start")


  def stop(self, env, rolling_restart=False):
    import params

    env.set_params(params)

    # pre-upgrade steps shutdown the datanode, so there's no need to call
    # action=stop
    if rolling_restart:
      datanode_upgrade.pre_upgrade_shutdown()
    else:
      datanode(action="stop")


  def configure(self, env):
    import params
    env.set_params(params)
    hdfs()
    datanode(action="configure")


  def status(self, env):
    import status_params

    env.set_params(status_params)
    check_process_status(status_params.datanode_pid_file)

  def security_status(self, env):
    import status_params

    env.set_params(status_params)
    props_value_check = {"hadoop.security.authentication": "kerberos",
                         "hadoop.security.authorization": "true"}
    props_empty_check = ["hadoop.security.auth_to_local"]
    props_read_check = None
    core_site_expectations = build_expectations('core-site', props_value_check, props_empty_check,
                                                props_read_check)
    props_value_check = None
    props_empty_check = ['dfs.datanode.keytab.file',
                         'dfs.datanode.kerberos.principal']
    props_read_check = ['dfs.datanode.keytab.file']
    hdfs_site_expectations = build_expectations('hdfs-site', props_value_check, props_empty_check,
                                                props_read_check)

    hdfs_expectations = {}
    hdfs_expectations.update(core_site_expectations)
    hdfs_expectations.update(hdfs_site_expectations)

    security_params = get_params_from_filesystem(status_params.hadoop_conf_dir,
                                                 ['core-site.xml', 'hdfs-site.xml'])
    result_issues = validate_security_config_properties(security_params, hdfs_expectations)
    if not result_issues:  # If all validations passed successfully
      try:
        # Double check the dict before calling execute
        if ('hdfs-site' not in security_params or
                'dfs.datanode.keytab.file' not in security_params['hdfs-site'] or
                'dfs.datanode.kerberos.principal' not in security_params['hdfs-site']):
          self.put_structured_out({"securityState": "UNSECURED"})
          self.put_structured_out(
            {"securityIssuesFound": "Keytab file or principal are not set property."})
          return

        cached_kinit_executor(status_params.kinit_path_local,
                              status_params.hdfs_user,
                              security_params['hdfs-site']['dfs.datanode.keytab.file'],
                              security_params['hdfs-site']['dfs.datanode.kerberos.principal'],
                              status_params.hostname,
                              status_params.tmp_dir,
                              30)
        self.put_structured_out({"securityState": "SECURED_KERBEROS"})
      except Exception as e:
        self.put_structured_out({"securityState": "ERROR"})
        self.put_structured_out({"securityStateErrorInfo": str(e)})
    else:
      issues = ""
      for cf in result_issues:
        issues += "Configuration file " + cf + " did not pass the validation. Reason: " + \
                  result_issues[cf]
      self.put_structured_out({"securityIssuesFound": issues})
      self.put_structured_out({"securityState": "UNSECURED"})


if __name__ == "__main__":
  DataNode().execute()