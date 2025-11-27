# This rule tells R8 to keep all classes, methods, and fields within the
# 'openpass.security' package and all of its sub-packages.
#
# -keep: The directive to preserve code.
# class: Specifies that we are keeping entire classes.
# openpass.security.**: The package name. The double asterisk (**) is a wildcard
#                        that matches the package and all sub-packages recursively.
# { *; }: This is the class member specification. The single asterisk (*) is a
#         wildcard that matches all fields and methods within the matched classes.

-keep class openpass.security.** { *; }

# It's also good practice to keep the default Android rules that come
# with a new project, as they handle common Android framework classes.
# Your default file will likely have other rules already in it.