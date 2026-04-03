#!/usr/bin/env perl

use Cwd qw(abs_path);
use File::Basename qw(dirname);
use File::Spec;

# include script functions
use vars qw(
	$PREFIX
);
$PREFIX = abs_path(dirname($0));
require(File::Spec->catfile($PREFIX, 'bin', 'functions.pl'));


if (not grep { $_ =~ /^[^-]/ } @ARGS) {
	debug("no maven targets specified, adding '" . join("', '", @DEFAULT_GOALS) . "' to the command-line");
	push(@ARGS, @DEFAULT_GOALS);
}

my @command = ($MVN, @ARGS);
if (defined $USE_ULIMIT_WRAPPER && $USE_ULIMIT_WRAPPER > 0) {
	my $cmd_str = join(' ', map { quotemeta($_) } @command);
	info("running (with ulimit -n $USE_ULIMIT_WRAPPER):", @command);
	handle_errors_and_exit(system("sh", "-c", "ulimit -n $USE_ULIMIT_WRAPPER 2>/dev/null; exec $cmd_str"));
} else {
	info("running:", @command);
	handle_errors_and_exit(system(@command));
}
