# English Style

Use these grammar, punctuation, and spelling rules for English prose in Chords
KDoc and Javadoc, Protobuf doc comments, other code comments, and Markdown.
For layout, links, terminology, and line wrapping, follow `AGENTS.md` and the
nearest documentation-writing skill.

This catalog is the source of truth for proofreading existing prose,
reviewing documentation changes, and writing new Chords documentation.

## Principles

1. Fix errors, not taste. Correct only what this catalog identifies as an
   error. Do not reword text that is already correct.
2. Make minimal edits. Preserve the author's wording, meaning, and voice.
3. Leave ambiguous cases unchanged and report them. A missed error is cheaper
   than an incorrect fix.
4. Never edit code or machine-read text.
5. Keep correct alternatives consistent within each file. Where no spelling
   dialect or list-punctuation convention dominates, leave the text unchanged
   and report the mix.

## Where English Prose Lives

| Language      | Prose to check                                                    |
|---------------|-------------------------------------------------------------------|
| Kotlin / Java | KDoc/Javadoc bodies and tag descriptions; block and line comments |
| Protobuf      | `//` doc comments for types and fields, and file-header prose      |
| Markdown      | Body prose and headings                                           |

Within a doc comment, edit only description text. Tag names, type references,
parameter names, and link targets are not prose.

## Never Edit

Skip the following content.

In every file:

- Code in any form: string literals, identifiers, fenced or indented code
  blocks, inline code spans, `{@code}` and `{@literal}` tags, `<pre>` and
  `<code>` HTML, `@sample` references, and documented commands.
- Generated files and generated sections, including root `pom.xml` and
  `dependencies.md` reports.
- License and copyright headers.
- Quoted text, URLs, email addresses, version strings, and file paths.
- Machine-read TODO prefixes. The description after the prefix remains prose.
- Editor and tool modelines.
- Text that a style guide cites as an example of an error, including the
  "Before" column of every table in this file. Those entries are deliberately
  wrong; correcting them destroys the catalog.

In Kotlin and Java:

- Inspection suppressions such as `//noinspection`.
- Javadoc and KDoc reference targets: `{@link}`, `{@linkplain}`, `@see`, and
  KDoc `[Symbol]` references.

In Protobuf:

- Lint directives such as `// buf:lint:ignore`.

In Markdown:

- YAML frontmatter, link-reference definitions, badge markup, and directive
  comments such as `<!-- markdownlint-disable -->`.

## Error Catalog

Group proofreading reports by these topic headings.

### Restrictive "Which" and "That"

Use "that" for a relative clause that restricts or identifies its antecedent.
Use "which", preceded by a comma, for a clause that adds information.

| Before                         | After                                      |
|--------------------------------|--------------------------------------------|
| a plugin which forces versions | a plugin that forces versions              |
| the file, which is generated   | the file, which is generated — leave alone |

Replace "which" with "that" only when none of these guards applies:

- A comma, opening parenthesis, or dash precedes "which".
- A preposition precedes it, as in "in which", "of which", or "with which".
- It is interrogative or determines a choice, as in "Which plugin?" or
  "decide which plugin".
- It starts a sentence.
- A hyphen abuts it in an identifier such as `which-fixer`.
- It appears in "that which" or "which is which".

### Articles

Add missing articles in complete sentences. Choose "a" or "an" by the sound
that follows.

| Before                                   | After                                           |
|------------------------------------------|-------------------------------------------------|
| Returns value of given field.            | Returns the value of the given field.           |
| Throws exception if file does not exist. | Throws an exception if the file does not exist. |
| a HTTP request                           | an HTTP request                                 |
| an user, an unique key                   | a user, a unique key                            |
| a SDK, an URL                            | an SDK, a URL                                   |

Leave unchanged:

- Plural and uncountable nouns used generically.
- Telegraphic headings, table cells, and list fragments.
- Initialisms whose pronunciation varies, such as SQL. Follow the file's
  established usage or report the ambiguity.
- Bare identifiers used as names, as in "Calls `close` after use."

### Subject-Verb Agreement

Make the verb agree with the grammatical subject, including the head noun of
a long subject and "there is/are" constructions.

| Before                              | After                              |
|-------------------------------------|------------------------------------|
| The methods returns a copy.         | The methods return a copy.         |
| Each of the listeners are notified. | Each of the listeners is notified. |
| The list of errors are cleared.     | The list of errors is cleared.     |
| There is several options.           | There are several options.         |

Leave unchanged:

- "data" used consistently as singular or plural within a file.
- A backticked identifier used as one named subject.
- "a number of X are" and "the number of X is".

### API Summary Verb Form

Describe what a function or method does in the third-person singular:
"Returns", "Creates", or "Validates". Do not use an imperative opener.

| Before                             | After                               |
|------------------------------------|-------------------------------------|
| `/** Return the current state. */` | `/** Returns the current state. */` |

Leave unchanged:

- Imperative instructions in procedures, tutorials, commands, and CLI help.
- Type summaries written as noun phrases or beginning with "Represents".
- "This method returns". It is wordy but grammatical.

### Prepositions

Fix only these established pairs. Other pairings can vary legitimately.

| Before                            | After                             |
|-----------------------------------|-----------------------------------|
| depends of                        | depends on                        |
| independent from                  | independent of                    |
| consists from                     | consists of                       |
| capable to handle                 | capable of handling               |
| waits the result                  | waits for the result              |
| listens the event                 | listens to the event              |
| in runtime, in compile time       | at runtime, at compile time       |
| on practice                       | in practice                       |
| on the screenshot, on the diagram | in the screenshot, in the diagram |
| typical for                       | typical of                        |
| access of the file                | access to the file                |

Leave unchanged:

- Correct transitive verbs such as "awaits", "accesses", and "discusses".
- "listens for" when it means awaiting a specific occurrence.
- Idioms such as "in search of" and "on the basis of".
- Dialect variants of "different from/to/than".

### Verb Complementation

"Allow", "enable", and "permit" need an object before a to-infinitive.
Without an object, use a gerund or rephrase. "Recommend" and "suggest" take a
gerund, not a bare infinitive.

| Before                        | After                        |
|-------------------------------|------------------------------|
| allows to configure the build | allows configuring the build |
| enables to run tests          | enables running tests        |
| permits to access the field   | permits access to the field  |
| We recommend to use the DSL.  | We recommend using the DSL.  |
| suggest to add a test         | suggest adding a test        |
| It is worth to note           | It is worth noting           |

Leave unchanged:

- A construction with an object, such as "allows the caller to configure".
- "allows for" followed by a noun.
- Passive "It is recommended to use".
- Either form of "helps (to) do".
- "provides the possibility to". Report it rather than automatically
  rewriting it because its grammaticality is disputed.

### Comparatives

Keep each comparison's function word: "greater than" and "equal to".

| Before                               | After                                      |
|--------------------------------------|--------------------------------------------|
| the day is less or equal zero        | the day is less than or equal to zero      |
| a value greater or equal the limit   | a value greater than or equal to the limit |
| the size is equal or greater than 10 | the size is greater than or equal to 10    |

Leave unchanged:

- Complete "greater than or equal to" and "less than or equal to" forms.
- Comparison operators in code spans.
- "no less than", "no more than", "at least", and "at most".
- Transitive "equals", as in "the result equals zero".

### Commas

Treat only these mechanical cases as errors:

- Add a comma after an introductory subordinate clause.
- Replace a comma splice with a semicolon or period.
- Remove a comma between a subject and its verb.
- Keep serial-comma usage internally consistent. Add a serial comma only when
  its absence is ambiguous; do not churn existing lists.

Leave commas inside quoted text unchanged. The restrictive
"which"/"that" topic owns the comma before "which". Either convention for a
comma after "e.g." or "i.e." is acceptable.

### Hyphenated Compound Modifiers

Hyphenate two words acting as one adjective before a noun.

| Before                    | After                     |
|---------------------------|---------------------------|
| read only mode            | read-only mode            |
| well known issue          | well-known issue          |
| case sensitive comparison | case-sensitive comparison |
| third party library       | third-party library       |
| long running task         | long-running task         |

Leave unchanged:

- Predicative use, as in "the mode is read only".
- Adverbs ending in "-ly", as in "fully qualified name".
- A number followed by a unit symbol, as in "a 5 GiB limit".

### Confusables

Correct these only when the intended meaning is unambiguous.

| Before                               | After                                 |
|--------------------------------------|---------------------------------------|
| The method returns it's result.      | The method returns its result.        |
| Let's you configure the build.       | Lets you configure the build.         |
| This value maybe null.               | This value may be null.               |
| more efficient then the default      | more efficient than the default       |
| Use this method to setup the server. | Use this method to set up the server. |
| Users can login with a token.        | Users can log in with a token.        |
| The server can not recover.          | The server cannot recover.            |

Use one word for nouns or adjectives and two for verbs: setup/set up,
login/log in, backup/back up, shutdown/shut down, and checkout/check out.
Use "cannot" except when "can not" deliberately means "is able not to".
Report ambiguous "e.g."/"i.e." and "affect"/"effect" cases.

### Punctuation and Spacing

- End a doc-comment summary sentence with a period.
- Use one space between sentences.
- Remove spaces before `.`, `,`, `;`, `:`, `?`, and `!`.
- Remove spaces just inside parentheses or brackets in prose.
- Remove duplicated terminal punctuation, but preserve `...` and `…`.
- Start sentences with a capital letter. Never change an identifier's case;
  reword the sentence if necessary.
- Add missing apostrophes in contractions.
- Keep list-item punctuation internally consistent. Fragments need no period.

### Duplicated and Dropped Words

Repair editing artifacts: a word repeated, two words run together, or a clause
left fused to its replacement. These are mechanical slips rather than
word-choice errors. Fix one only when the surrounding sentence makes the
intended wording certain.

| Before                               | After                            |
|--------------------------------------|----------------------------------|
| recommended to register registers it | recommended to register it       |
| the value is usedThe when searching  | the value is used when searching |
| The state is updated Text is parsed  | Text is parsed                   |

Leave unchanged:

- A correct repetition, as in "had had" or "that that".
- A run-together token that matches an identifier or belongs in a code span.
- A gap with more than one plausible completion. Report it instead.

### Spelling and Dialect

Correct genuine misspellings:

| Before                      | After                        |
|-----------------------------|------------------------------|
| recieve, occured, seperate  | receive, occurred, separate  |
| existance, paramter, lenght | existence, parameter, length |
| successfull, usefull        | successful, useful           |
| compatable, preferrable     | compatible, preferable       |

For American and British variants, keep each file internally consistent:

- Align a clear outlier with the file's dominant dialect.
- If neither dialect dominates, leave the text unchanged and report the mix.
- Treat `-or`/`-our`, `-er`/`-re`, licence/license, and gray/grey as strong
  markers. Treat `-ise`/`-ize` as weak evidence.
- Never change dialect in identifiers, code, quoted text, proper nouns, or
  prose that deliberately matches an identifier.

Chords has no repository-wide English dialect requirement.
