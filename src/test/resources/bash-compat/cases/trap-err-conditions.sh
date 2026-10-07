trap "echo ERR" ERR; if false; then :; fi; false || true; ! false; false && true; echo end
