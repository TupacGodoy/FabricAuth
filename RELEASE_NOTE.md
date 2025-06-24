##### Add
- Disable register command if `enable-global-password` enabled with `single-use-global-password` disabled

#### Fix
- Re-register `register` command if `single-use-global-password` was changed
- Wrong log-in message with enabled `single-use-global-password` and disabled `enable-global-password`