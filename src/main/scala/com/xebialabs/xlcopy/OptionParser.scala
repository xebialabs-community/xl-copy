package com.xebialabs.xlcopy


object OptionParser {
  type OptionMap = Map[Symbol, Any]
  type OptionMapBuilder = Map[String, Symbol]

  implicit class OptionMapImprovements(val m: Map[String, Symbol]) {
    def match_key(opt: String): String = {
      m.keys.find(_.matches( s""".*$opt(\\|.*)?""")).getOrElse("")
    }

    def match_get(opt: String): Option[Symbol] = {
      m.get(m.match_key(opt))
    }

    def match_apply(opt: String): Symbol = {
      m(m.match_key(opt))
    }
  }

  def validate(map: OptionMap, required: List[Symbol]): Option[OptionMap] = {
    required.forall(map.contains) match {
      case true => Some(map)
      case false => None
    }
  }

  def parseOptions(args: List[String],
                   positional: List[Symbol],
                   optional: OptionMapBuilder,
                   flags: OptionMapBuilder,
                   options: OptionMap = Map[Symbol, String](),
                   strict: Boolean = false
                  ): OptionMap = {
    args match {
      // Empty list
      case Nil => options

      // Options with values
      case key :: value :: tail if optional.match_get(key).isDefined =>
        parseOptions(tail, positional, optional, flags, options ++ Map(optional.match_apply(key) -> value))

      // Flags
      case key :: tail if flags.match_get(key).isDefined =>
        parseOptions(tail, positional, optional, flags, options ++ Map(flags.match_apply(key) -> true))

      // Positional arguments
      case value :: tail if positional != Nil =>
        parseOptions(tail, positional.tail, optional, flags, options ++ Map(positional.head -> value))

      // Generate symbols out of remaining arguments
      case value :: tail if !strict => parseOptions(tail, positional, optional, flags, options ++ Map(Symbol(value) -> value))

      case _ if strict =>
        printf("Unknown argument(s): %s\n", args.mkString(", "))
        sys.exit(1)
    }
  }
}
