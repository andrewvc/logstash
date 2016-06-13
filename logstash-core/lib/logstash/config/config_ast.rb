# encoding: utf-8
require 'logstash/errors'
require "treetop"

class Treetop::Runtime::SyntaxNode
  def significant # Should this element be ignored when building the EXPRs
    true
  end

  def expr
    require 'pry'; binding.pry
    if !elements || elements.empty?
      return [:nothing, self.inspect]
    else
      elements.map {|e| e.expr}
    end
  end

  def compile
    return "" if elements.nil?
    return elements.collect(&:compile).reject(&:empty?).join("")
  end

  def significant_exprs
    elements.select(&:significant)
  end

  # Traverse the syntax tree recursively.
  # The order should respect the order of the configuration file as it is read
  # and written by humans (and the order in which it is parsed).
  def recurse(e, depth=0, &block)
    r = block.call(e, depth)
    e.elements.each { |e| recurse(e, depth + 1, &block) } if r && e.elements
    nil
  end

  def recursive_inject(results=[], &block)
    if !elements.nil?
      elements.each do |element|
        if block.call(element)
          results << element
        else
          element.recursive_inject(results, &block)
        end
      end
    end
    return results
  end
  
  # When Treetop parses the configuration file
  # it will generate a tree, the generated tree will contain
  # a few `Empty` nodes to represent the actual space/tab or newline in the file.
  # Some of theses node will point to our concrete class.
  # To fetch a specific types of object we need to follow each branch
  # and ignore the empty nodes.
  def recursive_select(*klasses)
    return recursive_inject { |e| klasses.any? {|k| e.is_a?(k)} }
  end

  def recursive_inject_parent(results=[], &block)
    if !parent.nil?
      if block.call(parent)
        results << parent
      else
        parent.recursive_inject_parent(results, &block)
      end
    end
    return results
  end

  def recursive_select_parent(results=[], klass)
    return recursive_inject_parent(results) { |e| e.is_a?(klass) }
  end
end


module LogStash; module Config; module AST

  def self.defered_conditionals=(val)
    @defered_conditionals = val
  end

  def self.defered_conditionals
    @defered_conditionals
  end

  def self.defered_conditionals_index
    @defered_conditionals_index
  end

  def self.defered_conditionals_index=(val)
    @defered_conditionals_index = val
  end

  class Node < Treetop::Runtime::SyntaxNode
    def text_value_for_comments
      text_value.gsub(/[\r\n]/, " ")
    end
  end

  class Config < Node
    def compile
      LogStash::Config::AST.defered_conditionals = []
      LogStash::Config::AST.defered_conditionals_index = 0

      exprs = []

      sections = recursive_select(LogStash::Config::AST::PluginSection)
      sections.each do |s|
        #exprs << s.compile_initializer
      end

      # start inputs
      section_map = ::Hash.new {|h,k| h[k] = []}

      sections.each do |section|
        section_name = section.plugin_type.text_value.to_sym
        section_map[section_name] << section.expr
      end

      #exprs += definitions.join("\n").split("\n", -1).collect { |l| "  #{l}" }
      
      #exprs += LogStash::Config::AST.defered_conditionals

      
      require 'pry'; binding.pry
      return code.join("\n")      
    end
  end

  class Comment < Node
    def significant
      false
    end

  end
  
  class Whitespace < Node
    def significant
      false
    end
  end
  class PluginSection < Node
    # Global plugin numbering for the janky instance variable naming we use
    # like @filter_<name>_1
    def expr
      recursive_select(Branch, Plugin).map(&:expr).first
    end

    def section_type
      self.elements.first.text_value.to_sym
    end
    
    def initialize(*args)
      super(*args)
      @i = 0
    end

    # Generate ruby code to initialize all the plugins.
    def compile_initializer
      generate_variables
      code = []
      @variables.each do |plugin, name|

        code << <<-CODE
          @generated_objects[:#{name}] = #{plugin.compile_initializer}
          @#{plugin.plugin_type}s << @generated_objects[:#{name}]
        CODE

        # The flush method for this filter.
        if plugin.plugin_type == "filter"
          
          code << <<-CODE
            @generated_objects[:#{name}_flush] = lambda do |options, &block|
              @logger.debug? && @logger.debug(\"Flushing\", :plugin => @generated_objects[:#{name}])

              events = @generated_objects[:#{name}].flush(options)

              return if events.nil? || events.empty?

              @logger.debug? && @logger.debug(\"Flushing\", :plugin => @generated_objects[:#{name}], :events => events)

              #{plugin.compile_starting_here.gsub(/^/, "  ")}

              events.each{|e| block.call(e)}
            end

            if @generated_objects[:#{name}].respond_to?(:flush)
              @periodic_flushers << @generated_objects[:#{name}_flush] if @generated_objects[:#{name}].periodic_flush
              @shutdown_flushers << @generated_objects[:#{name}_flush]
            end
          CODE

        end
      end
      return code.join("\n")
    end

    def variable(object)
      generate_variables
      return @variables[object]
    end

    def generate_variables
      return if !@variables.nil?
      @variables = {}
      plugins = recursive_select(Plugin)

      plugins.each do |plugin|
        # Unique number for every plugin.
        @i += 1
        # store things as ivars, like @filter_grok_3
        var = :"#{plugin.plugin_type}_#{plugin.plugin_name}_#{@i}"
        # puts("var=#{var.inspect}")
        @variables[plugin] = var
      end
      return @variables
    end

  end

  class Plugins < Node; end
  class Plugin < Node
    def expr
      [:plugin, self.plugin_name, self.expr_attributes]
    end
    
    def plugin_type
      if recursive_select_parent(Plugin).any?
        return "codec"
      else
        return recursive_select_parent(PluginSection).first.plugin_type.text_value
      end
    end

    def plugin_name
      return name.text_value
    end

    def variable_name
      return recursive_select_parent(PluginSection).first.variable(self)
    end

    def expr_attributes
      # Turn attributes into a hash map
      self.attributes.recursive_select(Attribute).map(&:expr).reduce({}) do |hash,kv|
        k,v = kv        
        hash[k] = v
        hash
      end
       
    end

    def compile_initializer
      # If any parent is a Plugin, this must be a codec.

      if attributes.elements.nil?
        return "plugin(#{plugin_type.inspect}, #{plugin_name.inspect})" << (plugin_type == "codec" ? "" : "\n")
      else
        settings = attributes.recursive_select(Attribute).collect(&:compile).reject(&:empty?)

        attributes_code = "LogStash::Util.hash_merge_many(#{settings.map { |c| "{ #{c} }" }.join(", ")})"
        return "plugin(#{plugin_type.inspect}, #{plugin_name.inspect}, #{attributes_code})" << (plugin_type == "codec" ? "" : "\n")
      end
    end

    def compile
      case plugin_type
      when "input"
        return "start_input(@generated_objects[:#{variable_name}])"
      when "filter"
        return <<-CODE
          events = @generated_objects[:#{variable_name}].multi_filter(events)
        CODE
      when "output"
        return "targeted_outputs << @generated_objects[:#{variable_name}]\n"
      when "codec"
        settings = attributes.recursive_select(Attribute).collect(&:compile).reject(&:empty?)
        attributes_code = "LogStash::Util.hash_merge_many(#{settings.map { |c| "{ #{c} }" }.join(", ")})"
        return "plugin(#{plugin_type.inspect}, #{plugin_name.inspect}, #{attributes_code})"
      end
    end

    def compile_starting_here
      return unless plugin_type == "filter" # only filter supported.

      expressions = [
        LogStash::Config::AST::Branch,
        LogStash::Config::AST::Plugin
      ]
      code = []

      # Find the branch we are in, if any (the 'if' statement, etc)
      self_branch = recursive_select_parent(LogStash::Config::AST::BranchEntry).first

      # Find any siblings to our branch so we can skip them later.  For example,
      # if we are in an 'else if' we want to skip any sibling 'else if' or
      # 'else' blocks.
      branch_siblings = []
      if self_branch
        branch_siblings = recursive_select_parent(LogStash::Config::AST::Branch).first \
          .recursive_select(LogStash::Config::AST::BranchEntry) \
          .reject { |b| b == self_branch }
      end

      #ast = recursive_select_parent(LogStash::Config::AST::PluginSection).first
      ast = recursive_select_parent(LogStash::Config::AST::Config).first

      found = false
      recurse(ast) do |element, depth|
        next false if element.is_a?(LogStash::Config::AST::PluginSection) && element.plugin_type.text_value != "filter"
        if element == self
          found = true
          next false
        end
        if found && expressions.include?(element.class)
          code << element.compile
          next false
        end
        next false if branch_siblings.include?(element)
        next true
      end

      return code.collect { |l| "#{l}\n" }.join("")
    end # def compile_starting_here
  end

  class Name < Node
    def compile
      return text_value.inspect
    end
  end
  class Attribute < Node
    def expr
      [name.text_value, value.text_value]
    end
    
    def compile
      return %Q(#{name.compile} => #{value.compile})
    end
  end
  class RValue < Node; end
  class Value < RValue; end

  module Unicode
    def self.wrap(text)
      return "(" + text.force_encoding(Encoding::UTF_8).inspect + ")"
    end
  end

  class Bareword < Value
    def compile
      return Unicode.wrap(text_value)
    end
  end
  class String < Value
    def expr
      text_value[1...-1]
    end
    
    def compile
      return Unicode.wrap()
    end
  end
  class RegExp < Value
    def compile
      return "Regexp.new(" + Unicode.wrap(text_value[1...-1]) + ")"
    end
  end
  class Number < Value
    def expr
      text_value.include?(".") ? text_value.to_f : text_value.to_i
    end
    
    def compile
      return text_value
    end
  end
  class Array < Value
    def compile
      return "[" << recursive_select(Value).collect(&:compile).reject(&:empty?).join(", ") << "]"
    end
  end
  class Hash < Value
    def validate!
      duplicate_values = find_duplicate_keys

      if duplicate_values.size > 0
        raise ConfigurationError.new(
          I18n.t("logstash.runner.configuration.invalid_plugin_settings_duplicate_keys",
                 :keys => duplicate_values.join(', '),
                 :line => input.line_of(interval.first),
                 :column => input.column_of(interval.first),
                 :byte => interval.first + 1,
                 :after => input[0..interval.first]
                )
        )
      end
    end

    def find_duplicate_keys
      values = recursive_select(HashEntry).collect { |hash_entry| hash_entry.name.text_value }
      values.find_all { |v| values.count(v) > 1 }.uniq
    end

    def compile
      validate!
      return "{" << recursive_select(HashEntry).collect(&:compile).reject(&:empty?).join(", ") << "}"
    end
  end

  class HashEntries < Node
  end

  class HashEntry < Node
    def compile
      return %Q(#{name.compile} => #{value.compile})
    end
  end

  class Branch < Node
    def expr
      exprs = []
      else_stack = [] # For turning if / elsif / else into nested ifs
      self.recursive_select(Plugin, If, Elsif, Else).each do |node|        
        if node.is_a?(If)
          exprs << :if
          exprs << expr_cond(node)
          exprs << expr_body(node)
        elsif node.is_a?(Elsif)
          condition = expr_cond(node)
          body = expr_body(node)
          
          else_stack << [:if, condition, body]
        elsif node.is_a?(Else)
          body = expr_body(node)
          if else_stack.size > 1
            else_stack.last << body
          else
            exprs << body
          end
        end
      end

      exprs
    end

    def expr_cond(node)
      node.elements.find {|e| e.is_a?(Condition)}.expr
    end

    def expr_body(node)
      [:do, *node.recursive_select(Plugin, Branch).map(&:expr)]
    end
    
    def compile

      # this construct is non obvious. we need to loop through each event and apply the conditional.
      # each branch of a conditional will contain a construct (a filter for example) that also loops through
      # the events variable so we have to initialize it to [event] for the branch code.
      # at the end, events is returned to handle the case where no branch match and no branch code is executed
      # so we must make sure to return the current event.

      type = recursive_select_parent(PluginSection).first.plugin_type.text_value

      if type == "filter"
        i = LogStash::Config::AST.defered_conditionals_index += 1
        source = <<-CODE
          @generated_objects[:cond_func_#{i}] = lambda do |input_events|
            result = []
            input_events.each do |event|
              events = [event]
              #{super}
              end
              result += events
            end
            result
          end
        CODE
        LogStash::Config::AST.defered_conditionals << source

        <<-CODE
          events = @generated_objects[:cond_func_#{i}].call(events)
        CODE
      else # Output
        <<-CODE
          #{super}
          end
        CODE
      end
    end
  end

  class BranchEntry < Node; end

  class If < BranchEntry
    def expr
      require 'pry'; binding.pry
      return condition.compile
      children = recursive_inject { |e| e.is_a?(Branch) || e.is_a?(Plugin) }
      return "if #{condition.compile} # if #{condition.text_value_for_comments}\n" \
             << children.collect(&:compile).map { |s| s.split("\n", -1).map { |l| "  " + l }.join("\n") }.join("") << "\n"
    end
    
    def compile
      children = recursive_inject { |e| e.is_a?(Branch) || e.is_a?(Plugin) }
      return "if #{condition.compile} # if #{condition.text_value_for_comments}\n" \
        << children.collect(&:compile).map { |s| s.split("\n", -1).map { |l| "  " + l }.join("\n") }.join("") << "\n"
    end
  end
  class Elsif < BranchEntry
    def compile
      children = recursive_inject { |e| e.is_a?(Branch) || e.is_a?(Plugin) }
      return "elsif #{condition.compile} # else if #{condition.text_value_for_comments}\n" \
        << children.collect(&:compile).map { |s| s.split("\n", -1).map { |l| "  " + l }.join("\n") }.join("") << "\n"
    end
  end
  class Else < BranchEntry
    def compile
      children = recursive_inject { |e| e.is_a?(Branch) || e.is_a?(Plugin) }
      return "else\n" \
        << children.collect(&:compile).map { |s| s.split("\n", -1).map { |l| "  " + l }.join("\n") }.join("") << "\n"
    end
  end

  class Condition < Node
    def expr
      first_element = elements.first
      rest_elements = elements.size > 1 ? elements[1].recursive_select(BooleanOperator, Expression) : []
      
      all_elements = [first_element, *rest_elements]

      res = join_conditions(all_elements)
    end

    def precedence(op)
      #  Believe this is right for logstash?
      case op
      when :and
        2
      when :or
        1
      when :xor
        1
      when :nand
        1
      else
        raise ArgumentError, "Unexpected operator #{op}"
      end
    end

    def join_conditions(all_elements)
      # Use Dijkstra's shunting yard algorithm
      out = []
      operators = []

      all_elements.each do |e|
        e_exp = e.expr

        if e.is_a?(BooleanOperator)
          # TODO, are or/nand/xor all equal precedence?
          # We're treating them that way here, could be wrong
          # Not worth investigating for a PoC
          if operators.last && precedence(operators.last) > precedence(e_exp)
            out << operators.pop
          end
          operators << e_exp
        else
          out << e_exp
        end
      end
      operators.each {|o| out << o}

      stack = []
      expr = []
      out.each do |e|
        if e.is_a?(Symbol)
          stack << [e, stack.pop, stack.pop]
        else
          stack << e
        end
      end

      stack.first
    end
  end

  module Expression
    def expr
      return self.value if self.respond_to?(:value) 
      
      self.recursive_select(Condition, Expression).map {|e| e.respond_to?(:value) ? e.value : e.expr }      
    end
  end

  module NegativeExpression
    def value
      [:not, self.recursive_select(Condition).map(&:expr)]
    end
  end

  module ComparisonExpression
    def value
      lval, op, rval = self.recursive_select(Selector, ComparisonOperator, Number, String).map(&:expr)

      [op, lval, rval]
    end
  end

  module InExpression
    def value # Because this is somehow higher up the inheritance chain than Expression
      item, list = recursive_select(LogStash::Config::AST::RValue)
      [:in, list.expr, item.expr]
    end
  end

  module NotInExpression
    def value
      item, list = recursive_select(LogStash::Config::AST::RValue)
      return [:not [:in, list.expr, item.expr]]
    end
  end

  class MethodCall < Node
    def compile
      arguments = recursive_inject { |e| [String, Number, Selector, Array, MethodCall].any? { |c| e.is_a?(c) } }
      return "#{method.text_value}(" << arguments.collect(&:compile).join(", ") << ")"
    end
  end

  class RegexpExpression < Node
    def compile
      operator = recursive_select(LogStash::Config::AST::RegExpOperator).first.text_value
      item, regexp = recursive_select(LogStash::Config::AST::RValue)
      # Compile strings to regexp's
      if regexp.is_a?(LogStash::Config::AST::String)
        regexp = "/#{regexp.text_value[1..-2]}/"
      else
        regexp = regexp.compile
      end
      return "(#{item.compile} #{operator} #{regexp})"
    end
  end

  module ComparisonOperator
    def expr
      self.text_value.to_sym
    end
  end
  module RegExpOperator
    def expr
      [:re_match, self.text_value.to_sym]
    end    
  end
  module BooleanOperator
    def expr
      self.text_value.to_sym
    end
  end
  class Selector < RValue
    def expr
      [:get, text_value]
    end
  end
  class SelectorElement < Node; end
end; end; end


# Monkeypatch Treetop::Runtime::SyntaxNode's inspect method to skip
# any Whitespace or SyntaxNodes with no children.
class Treetop::Runtime::SyntaxNode
  def _inspect(indent="")
    em = extension_modules
    interesting_methods = methods-[em.last ? em.last.methods : nil]-self.class.instance_methods
    im = interesting_methods.size > 0 ? " (#{interesting_methods.join(",")})" : ""
    tv = text_value
    tv = "...#{tv[-20..-1]}" if tv.size > 20

    indent +
    self.class.to_s.sub(/.*:/,'') +
      em.map{|m| "+"+m.to_s.sub(/.*:/,'')}*"" +
      " offset=#{interval.first}" +
      ", #{tv.inspect}" +
      im +
      (elements && elements.size > 0 ?
        ":" +
          (elements.select { |e| !e.is_a?(LogStash::Config::AST::Whitespace) && e.elements && e.elements.size > 0 }||[]).map{|e|
      begin
        "\n"+e.inspect(indent+"  ")
      rescue  # Defend against inspect not taking a parameter
        "\n"+indent+" "+e.inspect
      end
          }.join("") :
        ""
      )
  end
end
