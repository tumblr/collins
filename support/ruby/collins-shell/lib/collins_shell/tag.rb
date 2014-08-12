require 'collins_shell/thor'
require 'collins_shell/util'
require 'thor'
require 'thor/group'

module CollinsShell

  class Tag < Thor

    include ThorHelper
    include CollinsShell::Util
    namespace :tag

    def self.banner task, namespace = true, subcommand = false
      "#{basename} #{task.formatted_usage(self, true, subcommand).gsub(':',' ')}"
    end

    desc 'list', 'list tags that are in use'
    use_collins_options
    def list
      call_collins get_collins_client, "list" do |client|
        header = [["Name", "Label", "Description"]]
        tags = header + client.get_all_tags.map do |tag|
          [tag.name, tag.label, tag.description]
        end
        print_table tags
      end
    end

    desc 'values TAG', 'list all values for the specified tag'
    use_collins_options
    def values tag
      call_collins get_collins_client, "values" do |client|
        client.get_tag_values(tag).sort.each do |value|
          puts(value)
        end
      end
    end
  end

end
