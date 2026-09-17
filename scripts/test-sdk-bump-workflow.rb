#!/usr/bin/env ruby
require 'yaml'
require 'minitest/autorun'
require 'open3'
require 'tmpdir'
require 'fileutils'
require 'json'

class KmpBumpWorkflowTest < Minitest::Test
  ROOT = File.expand_path('..', __dir__)
  UPSTREAM = 'https://github.com/notifly-tech/notifly-kmp-sdk.git'
  PLATFORMS = %w[android ios js].freeze

  def setup
    @tmp = Dir.mktmpdir('notifly-bump-check-')
    @env = {
      'GIT_CONFIG_GLOBAL' => '/dev/null', 'GIT_CONFIG_NOSYSTEM' => '1',
      'GIT_TERMINAL_PROMPT' => '0',
      'GIT_AUTHOR_NAME' => 'Fixture', 'GIT_AUTHOR_EMAIL' => 'fixture@example.invalid',
      'GIT_COMMITTER_NAME' => 'Fixture', 'GIT_COMMITTER_EMAIL' => 'fixture@example.invalid',
      'GIT_CONFIG_COUNT' => '2',
      'GIT_CONFIG_KEY_0' => 'protocol.file.allow', 'GIT_CONFIG_VALUE_0' => 'always',
      'GIT_CONFIG_KEY_1' => "url.#{@tmp}/upstream/.insteadOf", 'GIT_CONFIG_VALUE_1' => UPSTREAM,
      'KMP_VERSION' => 'v0.1.0-alpha.4', 'SDK_REPOSITORY' => 'team-michael/notifly-android-sdk', 'SDK_REPO_TOKEN' => 'fixture-only-not-a-token',
      'GITHUB_OUTPUT' => File.join(@tmp, 'output')
    }
    @source = File.join(@tmp, 'upstream')
    @host = File.join(@tmp, 'sdk')
    FileUtils.mkdir_p([@source, @host, File.join(@tmp, 'bin')])
    git(@source, 'init', '-b', 'main')
    File.write(File.join(@source, 'policy.txt'), "old\n")
    git(@source, 'add', '.')
    git(@source, 'commit', '-m', 'old')
    @old = git(@source, 'rev-parse', 'HEAD').strip
    File.write(File.join(@source, 'policy.txt'), "released\n")
    git(@source, 'commit', '-am', 'released')
    @released = git(@source, 'rev-parse', 'HEAD').strip
    git(@source, 'tag', @env.fetch('KMP_VERSION'))
    git(@source, 'tag', '-a', 'v0.1.0-beta.1', '-m', 'annotated release')
    File.write(File.join(@source, 'policy.txt'), "unreleased main\n")
    git(@source, 'commit', '-am', 'unreleased main')
    git(@host, 'init', '-b', 'main')
    git(@host, 'submodule', 'add', UPSTREAM, 'notifly-kmp-sdk')
    git(File.join(@host, 'notifly-kmp-sdk'), 'checkout', '--detach', @old)
    File.write(File.join(@host, 'version.txt'), "unchanged-sdk-version\n")
    git(@host, 'add', '.')
    git(@host, 'commit', '-m', 'host with pinned core')
    @env['PATH'] = "#{@tmp}/bin:#{ENV.fetch('PATH')}"
    File.write(File.join(@tmp, 'bin/gh'), <<~'SH')
      #!/usr/bin/env bash
      set -euo pipefail
      [[ "$1 $2" == 'release view' ]]
      [[ "$4 $5 $6 $7" == '--repo notifly-tech/notifly-kmp-sdk --json tagName,isDraft' ]]
      if [[ "${RELEASE_MISSING:-false}" == true ]]; then exit 1; fi
      printf '%s\n' "{\"tagName\":\"${RELEASE_TAG:-$3}\",\"isDraft\":${RELEASE_DRAFT:-false}}"
    SH
    FileUtils.chmod(0755, File.join(@tmp, 'bin/gh'))
  end

  def teardown
    FileUtils.remove_entry(@tmp)
  end

  def git(directory, *args)
    out, err, status = Open3.capture3(@env, 'git', *args, chdir: directory)
    assert status.success?, "git #{args.join(' ')}: #{out}\n#{err}"
    out
  end

  def workflow
    path = File.join(ROOT, '.github/workflows/bump-sdk-submodule.yml')
    assert File.file?(path), "Missing KMP-owned submodule update workflow"
    YAML.load_file(path)
  end

  def step(workflow, id)
    item = workflow.fetch('jobs').fetch('bump').fetch('steps').find { |candidate| candidate['id'] == id }
    assert item, "Missing workflow step: #{id}"
    item.fetch('run')
  end

  def run_step(workflow, id, extra = {})
    File.write(@env.fetch('GITHUB_OUTPUT'), '')
    out, err, status = Open3.capture3(@env.merge(extra), 'bash', '-e', '-o', 'pipefail', '-c', step(workflow, id), chdir: @host)
    [status.success?, out + err, File.read(@env.fetch('GITHUB_OUTPUT'))]
  end

  def test_dispatch_and_pr_boundary_contract
    config = workflow
    trigger = config.fetch('on') { config.fetch(true) }
    assert_equal ['workflow_call'], trigger.keys
    assert_equal true, trigger.dig('workflow_call', 'inputs', 'kmp_version', 'required')
    assert_equal({'contents' => 'read'}, config.fetch('permissions'))
    job = config.fetch('jobs').fetch('bump')
    assert_equal "github.repository == 'notifly-tech/notifly-kmp-sdk' && github.ref == 'refs/heads/main'", job.fetch('if')
    action = job.fetch('steps').find { |item| item.fetch('uses', '').start_with?('peter-evans/create-pull-request@') }
    assert action, 'The workflow must create or update a PR'
    assert_match(/@[0-9a-f]{40}\z/, action.fetch('uses'))
    assert_equal "steps.bump.outputs.changed == 'true'", action.fetch('if')
    assert_equal '${{ secrets.SDK_REPO_TOKEN }}', action.dig('with', 'token')
    assert_equal [
      'notifly-kmp-sdk',
      "${{ inputs.sdk_repository == 'team-michael/notifly-js-sdk' && 'package-lock.json' || '' }}"
    ], action.dig('with', 'add-paths').lines.map(&:strip)
    assert_equal 'main', action.dig('with', 'base')
    assert_equal 'automation/bump-kmp-${{ inputs.kmp_version }}', action.dig('with', 'branch')
    assert_equal false, action.dig('with', 'delete-branch')
    checkout = job.fetch('steps').find { |item| item.fetch('uses', '').start_with?('actions/checkout@') }
    assert_equal '${{ inputs.sdk_repository }}', checkout.dig('with', 'repository')
    assert_equal 'main', checkout.dig('with', 'ref')
    assert_equal false, checkout.dig('with', 'persist-credentials')
    assert_equal true, trigger.dig('workflow_call', 'inputs', 'sdk_repository', 'required')
    assert_equal true, trigger.dig('workflow_call', 'secrets', 'SDK_REPO_TOKEN', 'required')
  end

  def test_rejects_invalid_tags_and_missing_credentials
    config = workflow
    ['main', '336a194', '../main', 'v1.2.3/branch', "v1.2.3\nchanged=true", 'v1.2.3;touch /tmp/unwanted'].each do |tag|
      success, output = run_step(config, 'validate', 'KMP_VERSION' => tag)
      refute success, "Accepted invalid tag #{tag.inspect}: #{output}"
    end
    success, output = run_step(config, 'validate', 'SDK_REPO_TOKEN' => '')
    refute success, output
    %w[v0.1.0-alpha.4 v1.2.3 v1.2.3-beta.1].each do |tag|
      success, output = run_step(config, 'validate', 'KMP_VERSION' => tag)
      assert success, output
    end
  end

  def test_rejects_repositories_outside_the_three_platform_sdks
    config = workflow
    ['someone/another-sdk', 'team-michael/notifly-event', 'notifly-tech/notifly-kmp-sdk'].each do |repository|
      success, output = run_step(config, 'validate', 'SDK_REPOSITORY' => repository)
      refute success, output
    end
    PLATFORMS.each do |platform|
      success, output = run_step(config, 'validate', 'SDK_REPOSITORY' => "team-michael/notifly-#{platform}-sdk")
      assert success, output
    end
  end

  def test_requires_the_matching_published_release
    config = workflow
    success, output = run_step(config, 'release')
    assert success, output
    [{'RELEASE_DRAFT' => 'true'}, {'RELEASE_MISSING' => 'true'}, {'RELEASE_TAG' => 'v9.9.9'}].each do |extra|
      success, output = run_step(config, 'release', extra)
      refute success, output
    end
  end

  def test_updates_only_the_gitlink_to_the_tag_not_latest_main
    success, output, outputs = run_step(workflow, 'bump')
    assert success, output
    assert_equal @released, git(File.join(@host, 'notifly-kmp-sdk'), 'rev-parse', 'HEAD').strip
    assert_equal "notifly-kmp-sdk\n", git(@host, 'diff', '--name-only')
    assert_equal "unchanged-sdk-version\n", File.read(File.join(@host, 'version.txt'))
    assert_includes outputs, 'changed=true'
    assert_includes outputs, "previous_sha=#{@old}"
    assert_includes outputs, "next_sha=#{@released}"
  end

  def test_already_merged_version_is_a_noop
    config = workflow
    git(File.join(@host, 'notifly-kmp-sdk'), 'checkout', '--detach', @released)
    git(@host, 'add', 'notifly-kmp-sdk')
    git(@host, 'commit', '-m', 'already updated')
    success, output, outputs = run_step(config, 'bump')
    assert success, output
    assert_includes outputs, 'changed=false'
    assert_empty git(@host, 'status', '--porcelain')
  end

  def test_annotated_tags_are_resolved_to_commits
    success, output, outputs = run_step(workflow, 'bump', 'KMP_VERSION' => 'v0.1.0-beta.1')
    assert success, output
    assert_includes outputs, "next_sha=#{@released}"
  end

  def test_missing_tag_does_not_modify_the_pin
    success, output = run_step(workflow, 'bump', 'KMP_VERSION' => 'v8.8.8')
    refute success, output
    assert_equal @old, git(File.join(@host, 'notifly-kmp-sdk'), 'rev-parse', 'HEAD').strip
    assert_empty git(@host, 'diff', '--name-only')
  end

  def test_rejects_a_different_submodule_repository
    git(@host, 'config', '-f', '.gitmodules', 'submodule.notifly-kmp-sdk.url', 'https://example.invalid/untrusted.git')
    success, output = run_step(workflow, 'bump')
    refute success, output
    assert_equal @old, git(File.join(@host, 'notifly-kmp-sdk'), 'rev-parse', 'HEAD').strip
  end

  def test_lockfileRefresh_changedJsSdk_runsBetweenBumpAndPr
    steps = workflow.fetch('jobs').fetch('bump').fetch('steps')
    refresh = steps.find { |item| item['id'] == 'js-lockfile' }
    assert refresh, 'JS SDK updates must refresh the lockfile before creating a PR'
    condition = "steps.bump.outputs.changed == 'true' && inputs.sdk_repository == 'team-michael/notifly-js-sdk'"
    assert_equal condition, refresh.fetch('if')
    refute refresh.fetch('continue-on-error', false)
    java = steps.find { |item| item.fetch('uses', '').start_with?('actions/setup-java@') }
    node = steps.find { |item| item.fetch('uses', '').start_with?('actions/setup-node@') }
    npm = steps.find { |item| item['id'] == 'setup-npm' }
    assert java, 'JS builds require Java'
    assert node, 'Lockfile generation requires Node.js'
    assert npm, 'Lockfile generation requires a pinned npm version'
    [java, node, npm].each { |setup| assert_equal condition, setup.fetch('if') }
    assert_equal '17', java.dig('with', 'java-version')
    bump_index = steps.index { |item| item['id'] == 'bump' }
    pr_index = steps.index { |item| item.fetch('uses', '').start_with?('peter-evans/create-pull-request@') }
    assert_operator bump_index, :<, steps.index(java)
    assert_operator bump_index, :<, steps.index(node)
    assert_operator steps.index(java), :<, steps.index(refresh)
    assert_operator steps.index(node), :<, steps.index(refresh)
    assert_operator steps.index(node), :<, steps.index(npm)
    assert_operator steps.index(npm), :<, steps.index(refresh)
    assert_operator steps.index(refresh), :<, pr_index
  end

  def prepare_js_fixture
    @env.merge!('npm_config_cache' => File.join(@tmp, 'npm-cache'), 'npm_config_offline' => 'true')
    FileUtils.mkdir_p([File.join(@host, 'build/notifly-core-sdk'), File.join(@host, 'runtime')])
    File.write(File.join(@host, 'runtime/package.json'), JSON.generate(name: 'fixture-runtime', version: '1.0.0'))
    File.write(File.join(@host, 'build/notifly-core-sdk/package.json'), JSON.generate(name: 'notifly-core-sdk', version: '2.21.0'))
    File.write(File.join(@host, 'package.json'), JSON.generate(
      name: 'fixture-sdk', version: '2.21.0', private: true,
      workspaces: ['build/notifly-core-sdk'], dependencies: {'notifly-core-sdk' => '2.21.0'},
      scripts: {'build:kmp-core' => 'node build-core.cjs', 'preinstall' => 'exit 79'}
    ))
    File.write(File.join(@host, 'build-core.cjs'), <<~'JS')
      const fs = require('node:fs');
      if (process.env.FAIL_CORE_BUILD === 'true') process.exit(42);
      if (fs.readFileSync('notifly-kmp-sdk/policy.txt', 'utf8') !== 'released\n') process.exit(43);
      fs.writeFileSync('build/notifly-core-sdk/package.json', JSON.stringify({
        name: 'notifly-core-sdk', version: '2.21.0',
        dependencies: {'fixture-runtime': 'file:../../runtime'}
      }));
    JS
    out, err, status = Open3.capture3(@env, 'npm', 'install', '--package-lock-only', '--ignore-scripts', '--no-audit', '--no-fund', chdir: @host)
    assert status.success?, out + err
    @original_manifest = File.read(File.join(@host, 'package.json'))
    @original_lockfile = File.read(File.join(@host, 'package-lock.json'))
    git(@host, 'add', 'package.json', 'package-lock.json', 'build-core.cjs', 'runtime')
    git(@host, 'commit', '-m', 'JS fixture with old Core lockfile')
    success, output = run_step(workflow, 'bump')
    assert success, output
  end

  def test_lockfileRefresh_newCoreDependency_supportsCleanInstall
    prepare_js_fixture

    success, output = run_step(workflow, 'js-lockfile')

    assert success, output
    lockfile = JSON.parse(File.read(File.join(@host, 'package-lock.json')))
    assert_equal({'fixture-runtime' => 'file:../../runtime'}, lockfile.dig('packages', 'build/notifly-core-sdk', 'dependencies'))
    assert_equal @original_manifest, File.read(File.join(@host, 'package.json'))
    assert_equal %w[notifly-kmp-sdk package-lock.json], git(@host, 'diff', '--name-only').lines.map(&:strip)
    refute Dir.exist?(File.join(@host, 'node_modules')), 'Lockfile refresh must not install packages'
    out, err, status = Open3.capture3(@env, 'npm', 'ci', '--ignore-scripts', '--no-audit', '--no-fund', chdir: @host)
    assert status.success?, out + err
  end

  def test_lockfileRefresh_coreBuildFails_preservesLockfile
    prepare_js_fixture

    success, output = run_step(workflow, 'js-lockfile', 'FAIL_CORE_BUILD' => 'true')

    refute success, output
    assert_equal @original_lockfile, File.read(File.join(@host, 'package-lock.json'))
  end

  def test_lockfileRefresh_npmResolutionFails_stopsWorkflow
    prepare_js_fixture
    manifest = JSON.parse(@original_manifest)
    manifest['dependencies']['missing-fixture'] = 'file:missing-package.tgz'
    File.write(File.join(@host, 'package.json'), JSON.generate(manifest))

    success, output = run_step(workflow, 'js-lockfile')

    refute success, output
    assert_includes output, 'ENOENT'
    assert_equal @original_lockfile, File.read(File.join(@host, 'package-lock.json'))
  end
end
