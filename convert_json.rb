require 'json'
require 'pry'

json = JSON.parse(File.open('test.json', 'r') {|f| f.read})

processors = json["processors"]

edges = []

def short_id(id)
  "id#{id.split('-').first}"
end

out = "digraph G {\n"
processors.each do |id, props|
  begin
    short_id = short_id(id)
    
    if props["to"]
      props["to"].each do |to|
        label = to["metrics"]["eps"]
        out << "#{short_id} -> #{short_id(to["id"])} [label=\"#{label}\"]\n"
      end
    end
    
    label = if ["input", "filter", "output"].include?(props['type'])
      config = props['source_metadata']['source_text'].each_line.
        map(&:chomp).
        map {|l| l.gsub(/\s+/, ' ')}.
        map {|l| l.gsub(/\"/, "'")}.
        to_a.join(" ")
      metrics = props["metrics"].to_s.gsub(/\"/, '').gsub("=>", ":") rescue nil
      config + " | " + metrics
    elsif props['type'] == 'conditional'
      props['condition']
    elsif props['type'] == 'special'
      props['special_type']
    end
    out << "#{short_id} [label=\"#{label}\"]\n"
  rescue => e
    binding.pry
  end
end
out << "}"
puts out
