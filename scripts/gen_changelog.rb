require 'highline/import'
require 'octokit'

gh_oauth = ask("Please type your gh ouath token, and press enter:") { |q| q.echo = false }

client = Octokit::Client.new(:access_token => gh_oauth)

collins_repo_name = 'tumblr/collins'

latest_release = client.latest_release(collins_repo_name)

comparison = client.compare(collins_repo_name, "tags/#{latest_release.tag_name}", 'master')
comparison.commits.each do |c|
  # get the PR number
  /Merge pull request #(?<pr_num>\d+)/ =~ c.commit.message
  unless pr_num.nil?
    pr_num = pr_num.to_i
    pr = client.pull_request(collins_repo_name, pr_num)
    puts "- #{pr.title} \##{pr_num} @#{pr.user.login}"
  end
end
