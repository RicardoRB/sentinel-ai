package dev.ward.infrastructure.cli.init;

/** The terminal collaborators that ask init questions and show how many remain. */
record InitPrompts(InitWizard wizard, InitConfirmations confirmations, QuestionProgress progress) {}
