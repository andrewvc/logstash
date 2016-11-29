# encoding: utf-8
require "logstash/api/modules/base"
require "logstash/api/errors"
require "logstash/compiler"

module LogStash
  module Api
    module Modules
      class Config < ::LogStash::Api::Modules::Base
        get "/" do
          config_str = agent.pipelines.values.first.config_str
          pipeline = LogStash::Compiler.compile_pipeline(config_str)
          serialized = org.logstash.config.serializers.lsui.LSUIPipelineSerializer.serialize(pipeline)
          
          response = LogStash::Json.load(serialized)
          response["start_time_in_millis"] = factory.build(:stats).started_at
          response["plugins"] = factory.build(:plugins_command).run()
          
          stats_by_id = factory.build(:stats).
            pipeline[:plugins].values.flatten.
            reduce({}) {|acc,h| acc[h[:id].to_s] = h; ;acc}
          
          response["processors"].each do |id,properties|
            stats = stats_by_id[id] || {}
            properties["metrics"] = stats
            (properties["to"] || []).each do |to_entry|
              eps = stats[:events] ? stats[:events][:out] : nil
              to_entry["metrics"] = { 
                "eps" => eps 
              }
            end
          end
          
          
          # TODO: Remove this before release!
          headers["Access-Control-Allow-Origin"] = "*"
          respond_with(response, {:as => :json})
        end

      end
    end
  end
end
