(ns lodr.parser
  (:require [instaparse.core :as insta]))

(def query-parser
  (insta/parser
    "expr = s-exp
    (* these can be top level expr on their own *)
    <s-exp> = or-and | is-not | from |parens
    (* parens can contain any expr *)
    <parens> = <'('> s-exp <')'>
    <or-and> = or | and
    (* s-exp on RHS is too greedy *)
    or = s-exp <'|'|'OR'> (is-not | from | parens)
    and = s-exp <'+'|'AND'> (is-not | from | parens)
    <is-not> = is | not
    is = field <':'|'IS'> value
    not = field <'!:'|'IS NOT'> value
    from = field <'@'|'FROM'> ranges
    <ranges> = (range <'|'|'OR'>)+ range
    range = date <'-'|'TO'> date
    <date> = #'[0-9]+'
    (* fields cannot be free text *)
    field = word
    (* values can be a term or a list of terms*)
    value = terms | term
    (* terms separatd by OR, must have at least term OR term*)
    <terms> = (term <'|'|'OR'>)+ term
    (* term can be free text or a word *)
    <term> = text | word
    (* free text can support spaces but must be quoted *)
    <text> = <'\"'> #'[A-Za-z0-9-_ ]+' <'\"'>
    (* a word cannot have a space *)
    <word> = #'[A-Za-z0-9-_.]+'"
    :string-ci true
    :input-format :ebnf
    :output-format :enlive
    :auto-whitespace :standard
    ))

(defn parse [input]
  (let [transform-options {:field str :range (comp vec list) :value (comp vec list) :expr identity}]
    (->> (query-parser input)
         (insta/transform transform-options))))
