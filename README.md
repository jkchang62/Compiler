Compiler with the following analysis phases and features:
- Syntactic Analysis:
  - LL(1) parser.
  - Abstract syntax tree (AST) constructor.
  - Operator precedence in expressions.
- Two-Pass Contextual Analysis:
  - Type checking (e.g. improper return types, parameters/arguments, statics, etc.).
  - Identification (e.g. proper declaration of variables/variable names, public/private accessibility, improper denotations, etc.). 
- Code generation.
  - Runs code using an abstract machine. 
- Current language capable of being compiled, written in extended Backus-Naur form (EBNF): 
  - [Language.txt](https://github.com/jkchang62/Compiler/files/6896634/Language.txt)


