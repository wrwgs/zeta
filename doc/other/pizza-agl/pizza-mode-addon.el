;; ADD-ONS FOR PIZZA MODE
;; $Id: pizza-mode-addon.el,v 1.5 1998/07/08 08:46:01 wg Exp $ 
;; by Wolfgang Grieskamp (wg@cs.tu-berlin.de)

;; Some add-ons to the pizza-mode, actually the underlying Java style
;; of cc-mode:
;;
;; - array initializers, anonymous classes, and funs are indended
;;   relatively to the introductionary keyword:
;;   if the line after the opening brace is empty, e.g.:
;; 
;;     return new Class(...) {
;;              public A foo(){
;;                ...
;;              }
;;            };
;;
;;     agg.map(fun (A x)->B {
;;               ...
;;             });
;;  
;;   if it is non-empty, then e.g.:
;;
;;     new A[] { a,b,c
;;               d,e,f
;;             }
;; 
;; - chains of methods calls are indended relatively to  a `.' on a
;;   a previous line, e.g.:
;;     
;;     result = new HashMap().define(d.x,d.y)
;;                           .define(...)
;; 
;; - argument list of method calls, if they start on the next line,
;;   are indended relatively to the method name, e.g.:
;;
;;     result = foo(
;;                arg1, arg2,
;;                arg3
;;              ); 
;;
;;   if they start on the same line, they are indended as usually.
;;   In addition, a closing brace on a single line is aligned
;;   with the opening brace:
;;
;;     result = foo(arg1, arg2,
;;                  arg3
;;                 ); 
;;
;;
;; These modifications have been only tested for XEmacs 20.4.
;;
;;
;; Technical Note: it would perhaps be much more efficient if the 
;; cc-engine allows customization of recognition of inline blocks 
;; and brace-lists.



;; Regexps

(defconst pizza-ident-regexp 
  ;; regexp matching a Pizza ident. 
  "\\w+\\([.]\\w+\\)*")

(defconst pizza-between-fun-and-brace-regexp
  ;; regexp which captures the characters inbetween a fun keyword
  ;; and the starting brace of the attached block (an exact match
  ;; cannot be described with regular expressions because of
  ;; generic parameter lists)
  "\\(\\w\\|\\s \\|[-><.,\n()]\\)*")


(defconst pizza-anon-block-header-regexp
  ;; regexp to match a header of an anonymous block
  ;; (not including the argument list of a class but including
  ;;  complete headers of Pizza fun's)
  (concat
					; array & class creation
   "new[ \n\t\f]+" 
     pizza-ident-regexp 
     "[ \n\t\f]*\\([[][ \n\t\f]*[]]\\)?[ \n\t\f]*" 
   "\\|"
					; funs
   "fun\\>" pizza-between-fun-and-brace-regexp
   ))

(defconst pizza-anon-block-regexp-array-pos 2)
  

(defconst pizza-brace-close-line-regexp
  ;; regexp matching a line containing a close brace in front
  "^\\s *}")

(defconst pizza-rest-empty-line-regexp
  ;; regexp matching whitespace (including line comment) at end of line
  "\\s *\\(//[^\n]*\\)?$")

(defconst pizza-arglist-intro-after-paren-regexp
  ;; regexp matching an `ident(<space>' at the end of a line
  (concat pizza-ident-regexp "\\s-*(" pizza-rest-empty-line-regexp))


;; Auxiliaries

(defsubst pizza-end-of-line ()
  ;; position point at the logical end of the current line.
  (beginning-of-line)
  (if (looking-at "^.*\\(\\s *//[^\n]*\\)$")
      (goto-char (match-beginning 1))
    (end-of-line)))
  

(defsubst pizza-skip-space-backward ()
  ;; skip space backward, regognizing line comments (only if we are
  ;; not IN a line comment)
  (let (exit)
    (while (not exit)
      (skip-syntax-backward " ")
      (backward-char 1)
      (if (looking-at "\n")
	  (progn
	    (backward-char 1)
	    (pizza-end-of-line))
	(forward-char 1)
	(setq exit t)))))
      
  
(defun pizza-skip-chain-backward ()
  ;; skip backward a method call chain on the same line (such as f.x(a()).c())
  (let (exit)
    (while (not exit)
      (skip-syntax-backward " ")
      (backward-char 1)
      (while (looking-at ")") ;; there may be several groups in Pizza
	(progn
	  (backward-up-list 1)
	  (skip-syntax-backward " ")
	  (backward-char 1)))
      (forward-char 1)
      (skip-syntax-backward "w")
      (skip-syntax-backward " ")
      (backward-char 1)
      (if (not (looking-at "[.]"))
	  (progn
	    (forward-char 1)
	    (skip-syntax-forward " ")
	    (setq exit t))))))
	  
      
(defun pizza-search-anon-block-header ()
  ;; search backward from point, which is at an opening brace, whether
  ;; a header (array initializer, anonymous class, or fun) is
  ;; attached. This cannot be done using a simple regexp alone, since
  ;; we have to treat nested parameter lists of anonymous classes.
  ;; return pair of absolute indentation column of the block and whether
  ;; its an array or not, or nil if no header is found
  (let ((currpos (point))
	limit header-start search-start is-array)
    ;; first skip possible parameters of new Class(...)
    (pizza-skip-space-backward)
    (backward-char 1)
    (if (looking-at ")")
	(progn
	  ;; skip a class parameter block
	  (backward-up-list 1)
	  (pizza-skip-space-backward))
      (forward-char 1))
    ;; for the actual header take a regexp
    (setq limit
	  (save-excursion
	    (forward-line -6)     ; give 6 lines for the header of fun's.
	                          ; this should be enough for practical
				  ; concerns ...
	    (point)))
    (setq search-start (point))
    (if (or (not (re-search-backward pizza-anon-block-header-regexp limit t))
	    (not (= (match-end 0) search-start)))
	nil
      ;; here we are. 
      (setq is-array (and (match-beginning pizza-anon-block-regexp-array-pos) 
			  (> (match-end pizza-anon-block-regexp-array-pos)
			     (match-beginning 
			      pizza-anon-block-regexp-array-pos))))
      (setq header-start (match-beginning 0))
      (goto-char currpos)
      (forward-char 1)
      (if (looking-at pizza-rest-empty-line-regexp)
	  ;; a block of style
	  ;;   header {
	  ;;     ...
	  ;; use start of header as column
	  (progn
	    (goto-char header-start)
	    (cons (current-column) is-array))
	;; a block of style
	;;   header { ...
	;; use column of brace
	(goto-char currpos)
	(cons (current-column) is-array)))))


  

;; Anon blocks

(defun pizza-lineup-anon-block (langelem)
  ;; cc-mode lineup command for anon blocks. will be attached to
  ;; several syntax symbols.
  (save-excursion
    (let ((currpos (point))
	  (syntax (car langelem))
	  (elempos (cdr langelem)) 
	  elemcol
	  header
	  headercol
	  isarray)
      (goto-char elempos)
      (setq elemcol (current-column))
      (goto-char currpos)
      (beginning-of-line)
      (backward-up-list 1)
      (if (not (setq header (pizza-search-anon-block-header)))
	  ;; make standard indentation
	  (cond ((eq syntax 'statement) 0)
		((eq syntax 'block-close) 0)
		(t c-basic-offset))
	;; see if this is closing brace
	(setq headercol (car header)
	      isarray (cdr header))
	(goto-char currpos)
	(beginning-of-line)
	(if (looking-at pizza-brace-close-line-regexp)
	    (- headercol elemcol)
	  (let ((offs (+ (- headercol elemcol) c-basic-offset)))
	    (if (and (eq syntax 'statement-cont) (not isarray))
		;; add one more for continuation indent
		(+ offs c-basic-offset)
	      offs)))))))
	  
  
;; Method Call Chains

(defun pizza-lineup-method-chain (langelem)
  ;; cc-mode lineup command for method chains, will be attached to
  ;; several syntax symbols.
  (let ((ind
	 (save-excursion
	   (let ((currpos (point))
		 (elempos (cdr langelem)) 
		 elemcol begin-line start-search)
	     (goto-char elempos)
	     (setq elemcol (current-column))
	     (goto-char currpos)
	     (beginning-of-line)
	     (pizza-skip-space-backward)
	     (pizza-skip-chain-backward)
	     (skip-syntax-forward "w")
	     (skip-syntax-forward " ")
	     (if (not (looking-at "[.]\\|("))
		 nil
	       (- (current-column) elemcol))))))
    (cond (ind ind)
	  ((eq (car langelem) 'statement-cont)
	   (pizza-lineup-anon-block langelem))
	  ((eq (car langelem) 'arglist-cont-nonempty)
	   (c-lineup-arglist langelem))
	  (t 0))))
	  
;; Argument Lists

(defun pizza-lineup-arglist-intro-after-paren (langelem)
  ;; cc-mode lineup function to indent an arglist-intro line.
 (save-excursion
   (let ((elempos (cdr langelem)) elemcol)
      (goto-char elempos)
      (setq elemcol (current-column))
      (pizza-end-of-line)
      (skip-syntax-backward " ")
      (backward-char 1) ;; the guaranteed (
      (pizza-skip-chain-backward)
      (+ (- (current-column) elemcol) c-basic-offset))))
 

(defun pizza-lineup-close-paren (langelem)
  ;; cc-mode lineup function to indent a closing paren.
  (save-excursion
    (let ((currpos (point))
	  (elempos (cdr langelem)) 
	  elemcol)
      (goto-char elempos)
      (setq elemcol (current-column))
      (pizza-end-of-line)
      (skip-syntax-backward " ")
      (backward-char 1) 
      (if (not (looking-at "("))
	  (progn 
	    ;; code follows, align with opening paren
	    (goto-char currpos)
	    (beginning-of-line)
	    (backward-up-list 1)
	    (- (current-column) elemcol))
	;; align with call chain
	(pizza-skip-chain-backward)
	(- (current-column) elemcol)))))
	


;; Installation

(defun pizza-addon-hook ()

  ;; Define indentation methods
  (if (not (local-variable-p 'c-offsets-alist (current-buffer) t))
      (progn
	;; don't know if this is strictly necessary, but let's be sure
	(make-local-variable 'c-offsets-alist)
	(setq c-offsets-alist (purecopy c-offsets-alist))))
  (c-set-offset 'arglist-intro 'pizza-lineup-arglist-intro-after-paren t)
  (c-set-offset 'arglist-close 'pizza-lineup-close-paren t)
  (c-set-offset 'statement 'pizza-lineup-anon-block t)
  (c-set-offset 'statement-cont 'pizza-lineup-method-chain t)
  (c-set-offset 'arglist-cont 'pizza-lineup-method-chain t)
  (c-set-offset 'arglist-cont-nonempty 'pizza-lineup-method-chain t)
  (c-set-offset 'statement-block-intro 'pizza-lineup-anon-block t)
  (c-set-offset 'block-close 'pizza-lineup-anon-block t)
  
  )

(add-hook 'pizza-mode-hook 'pizza-addon-hook)

(provide 'pizza-mode-addon)
