# Chapter 2: Codebase Analyzer

Welcome back! In [Chapter 1: Tutorial Orchestration Flow](01_tutorial_orchestration_flow.md), we learned about the "project manager" of our `tutorial-clj` system, the component that coordinates all the steps to generate a tutorial. We saw that the very first task this manager delegates is to understand the code itself.

Now, let's meet the component responsible for this initial exploration: the **Codebase Analyzer**.

## What's the Job of the Codebase Analyzer? Our Digital Librarian

Imagine you want to write a book report, but first, you need to actually *get* the book and read it. You can't analyze what you don't have! Similarly, before our `tutorial-clj` system can create a tutorial for a Clojure project, it first needs to "read" that project's code.

This is where the **Codebase Analyzer** steps in. Think of it as a very diligent and organized librarian or archivist for your code. Its main job is to:

1.  **Find the Code:** Systematically scan a given Clojure project to locate all the relevant code files (like `.clj` or `.cljs` files). It knows to ignore things that aren't code, like images or documentation files.
2.  **Read the Code:** Once it finds these files, it reads their actual content – the lines of Clojure code you've written.
3.  **Organize for Understanding:** Finally, it takes all this gathered information (filenames and their content) and packages it into a neat, structured format. This structured package is often called the "context," and it's what our system, especially the AI parts, will use for further analysis.

Without the Codebase Analyzer, our system would be like trying to describe a forest without ever stepping into it or looking at the trees!

## How Does It Work? A Peek Behind the Curtain

Let's say our [Tutorial Orchestration Flow](01_tutorial_orchestration_flow.md) tells the Codebase Analyzer, "Hey, I need you to look at this Clojure project located at `/my-projects/cool-app`."

Here's a simplified idea of what the Codebase Analyzer does next:

1.  **Input:** It takes the path to the project (e.g., `/my-projects/cool-app`).
2.  **The Scan:**
    *   It starts exploring the `cool-app` folder.
    *   It looks into subfolders like `src` (where Clojure source code usually lives).
    *   It identifies files that are likely to contain Clojure code. How? Often by looking at their endings, called file extensions (e.g., `.clj`, `.cljs`, `.cljc`).
3.  **The Read:**
    *   For each Clojure file it finds (say, `src/core.clj`), it opens the file and reads all the text inside it.
4.  **The Organization (Output):**
    *   It then creates a list. Each item in this list represents a code file and contains two key pieces of information:
        *   The **path** to the file (e.g., `src/core.clj`).
        *   The **content** of the file (the actual Clojure code as a chunk of text).

This organized list of file paths and their contents is the "structured context" we talked about. It's the foundational dataset that all other parts of `tutorial-clj` will build upon.

**Example Time!**

Imagine your Clojure project is very small and has only one code file:
*   Project location: `/my-tiny-project`
*   File inside: `src/main.clj`
*   Content of `src/main.clj`:
    ```clojure
    (ns my-tiny-project.main)

    (defn greet [name]
      (str "Hello, " name "!"))

    (println (greet "World"))
    ```

If we ask the Codebase Analyzer to process `/my-tiny-project`, its output would conceptually look something like this (imagine it as a list containing one item):

```
[
  {
    :path "src/main.clj",
    :content "(ns my-tiny-project.main)\n\n(defn greet [name]\n  (str \"Hello, \" name \"!\"))\n\n(println (greet \"World\"))"
  }
]
```

This output is super useful! It tells us exactly which file was found and what code is inside it, all ready for the next steps. If there were more Clojure files, they'd each have their own entry in this list.

## Why is This "Structured Context" So Important?

This collection of code files and their content, prepared by the Codebase Analyzer, is crucial because:

*   **It's the Raw Material:** Just like a chef needs ingredients before cooking, our system needs this raw code before it can do anything else.
*   **Foundation for Discovery:** The next component, the [Abstraction Discovery Engine](03_abstraction_discovery_engine.md), will use this structured context to identify the main concepts and "big ideas" within your code.
*   **Source for Examples:** Later, when the [Tutorial Content Generation Core](04_tutorial_content_generation_core.md) writes explanations, it might pull snippets of code directly from this context to use as examples.

Without this first step of gathering and organizing the code, the rest of the tutorial generation process simply can't happen.

## Under the Hood: A Simple Flow

Let's visualize the Codebase Analyzer's interaction when it gets a request:

```mermaid
sequenceDiagram
    participant TOF as Tutorial Orchestration Flow
    participant CA as Codebase Analyzer
    participant FS as Operating System File System
    participant CodeFile as Individual Code File

    TOF->>CA: Analyze project at "/my-projects/cool-app"
    Note over CA: I need to find all Clojure files!
    CA->>FS: Give me a list of all files and folders in "/my-projects/cool-app"
    FS-->>CA: Here's the list (e.g., "src/", "project.clj", "README.md")
    Note over CA: Okay, "src/" looks interesting. Let's check inside.
    CA->>FS: What's inside "/my-projects/cool-app/src/"?
    FS-->>CA: Found "core.clj", "utils.cljs"
    Note over CA: Found "core.clj"! It's a .clj file. Need its content.
    CA->>CodeFile: Read content of "src/core.clj"
    CodeFile-->>CA: (Content of core.clj as text)
    Note over CA: Found "utils.cljs"! It's a .cljs file. Need its content.
    CA->>CodeFile: Read content of "src/utils.cljs"
    CodeFile-->>CA: (Content of utils.cljs as text)
    Note over CA: Now I'll package these (path, content) pairs.
    CA-->>TOF: Here's the structured code context for "cool-app".
end
```

This diagram shows the Codebase Analyzer interacting with the file system to find files and then reading those files to get their content, before finally passing the organized information back.

## A Glimpse at the Code (Simplified)

While the actual code in `tutorial-clj` might be more detailed, let's look at some conceptual Clojure snippets to understand how this could be implemented.

**1. Finding Clojure Files**

First, we need a way to search through a project directory and pick out only the Clojure files.

```clojure
(ns tutorial-clj.codebase-analyzer
  (:require [clojure.java.io :as io])) ; For working with files

(defn find-clojure-files
  "Recursively finds all Clojure files (.clj, .cljs, .cljc) in a directory."
  [project-root-path]
  (let [project-dir (io/file project-root-path)]
    (->> (file-seq project-dir) ; 1. Get ALL files and folders under project-root-path
         (filter #(.isFile %))   ; 2. Keep only the actual files (not directories)
         (filter #(let [file-name (.getName %)] ; 3. For each file, check its name
                    (or (.endsWith file-name ".clj")   ; Is it a .clj file?
                        (.endsWith file-name ".cljs")  ; Or a .cljs file?
                        (.endsWith file-name ".cljc")))) ; Or a .cljc file?
         (map #(.getPath %))))) ; 4. If yes, get its full path as a string
```

**Explanation:**

*   `ns tutorial-clj.codebase-analyzer ...`: This declares our namespace and requires `clojure.java.io` which helps with file operations.
*   `find-clojure-files [project-root-path]`: Defines a function that takes the path to your project's main folder.
*   `(file-seq project-dir)`: This is a handy Clojure function that gives you a sequence of *all* files and directories within `project-dir`, including those in subfolders.
*   `(filter #(.isFile %))`: We then filter this sequence to keep only items that are actual files (not directories).
*   The next `filter` checks if the file's name ends with common Clojure extensions (`.clj`, `.cljs`, `.cljc`).
*   `(map #(.getPath %))`: For every file that passes the filters, we get its full path (e.g., `/my-projects/cool-app/src/core.clj`).
*   This function would return a list of paths to all Clojure files in the project.

**2. Reading File Content**

Once we have the path to a file, we need to read its content.

```clojure
(defn read-file-content
  "Reads a file and returns its path and content as a map."
  [file-path-string]
  {:path file-path-string  ; Store the path we were given
   :content (try
              (slurp file-path-string) ; 1. Read the entire file content into a string
              (catch Exception e        ; 2. If anything goes wrong (e.g., file not found)
                (str "Error reading file: " file-path-string)))}) ; Return an error message
```

**Explanation:**

*   `read-file-content [file-path-string]`: This function takes a single file path (that we got from `find-clojure-files`).
*   `(slurp file-path-string)`: `slurp` is a Clojure function that "slurps" up the entire content of a file and gives it to you as a single string. This is perfect for getting all the code from a file.
*   The `try...catch` block is for error handling. If `slurp` fails (e.g., the file doesn't exist or we don't have permission to read it), it "catches" the error and returns a helpful message instead of crashing.
*   The function returns a map containing the original `:path` and the `:content` (or an error message). This is one piece of our "structured context."

**3. Putting It All Together: The `analyze-codebase` function**

Now, let's combine these to create our main Codebase Analyzer function:

```clojure
(defn analyze-codebase
  "Scans a project directory, reads Clojure files, and returns structured data."
  [project-root-path]
  (let [clojure-file-paths (find-clojure-files project-root-path)] ; Step A
    ;; Step B: For each found file path, read its content and structure it
    (mapv read-file-content clojure-file-paths)))
```

**Explanation:**

*   `analyze-codebase [project-root-path]`: This is our main function for this component.
*   **Step A:** `(find-clojure-files project-root-path)`: It first calls our `find-clojure-files` function to get a list of all Clojure file paths in the project.
*   **Step B:** `(mapv read-file-content clojure-file-paths)`:
    *   `mapv` is a Clojure function that takes another function (`read-file-content` in this case) and a collection (`clojure-file-paths`).
    *   It applies `read-file-content` to *each item* in `clojure-file-paths`.
    *   So, for every file path we found, it calls `read-file-content` to get the map containing its `:path` and `:content`.
    *   The result is a vector (a type of list) of these maps – exactly the structured context we want!

**Example Output (Conceptual):**

If `/my-project` had `src/core.clj` (content: `(ns app.core)`) and `src/utils.clj` (content: `(ns app.utils)`), calling `(analyze-codebase "/my-project")` would produce:

```clojure
[{:path "/my-project/src/core.clj", :content "(ns app.core)"}
 {:path "/my-project/src/utils.clj", :content "(ns app.utils)"}]
```

This output is precisely what the [Tutorial Orchestration Flow](01_tutorial_orchestration_flow.md) needs to pass on to the next stage of processing.

## Conclusion

And that's the Codebase Analyzer! We've learned that it acts like our system's diligent librarian, responsible for:
1.  **Locating** all relevant Clojure source code files within a project.
2.  **Reading** the actual code content from these files.
3.  **Organizing** this information (file paths and their content) into a structured format that the rest of the `tutorial-clj` system can easily work with.

This "structured context" is the essential first batch of ingredients. Without it, our tutorial-generating chef would have an empty kitchen!

Now that we have gathered and organized all the raw code, what's next? We need to start making sense of it! In the next chapter, we'll explore the [Abstraction Discovery Engine](03_abstraction_discovery_engine.md), which takes this raw code and tries to identify the key concepts and "big ideas" hidden within.

---

Generated by [AI Codebase Knowledge Builder](https://github.com/The-Pocket/Tutorial-Codebase-Knowledge)