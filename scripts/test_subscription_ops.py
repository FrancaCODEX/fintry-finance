import unittest
import uuid

import ops
import subscription_ops


class SubscriptionOperatorTests(unittest.TestCase):
    def test_rejects_unbounded_periods_and_sql_injection_before_accessing_database(self):
        identifier = str(uuid.uuid4())
        for days in (0, -1, 367):
            with self.assertRaises(ops.OpsError):
                subscription_ops.activation_sql(identifier, days, "invoice-1")
        for value in ("'; DROP TABLE companies;--", "ref\nsecret", "x" * 121, ""):
            with self.assertRaises(ops.OpsError):
                subscription_ops.activation_sql(identifier, 30, value)
        with self.assertRaises(ops.OpsError):
            subscription_ops.activation_sql("invalid;SQL", 30, "invoice")

    def test_default_cli_only_previews_and_never_applies_activation(self):
        from contextlib import redirect_stdout
        from io import StringIO
        from unittest.mock import patch
        import json
        identifier=str(uuid.uuid4())
        output=StringIO()
        with patch("sys.argv",["subscription_ops.py","--request",identifier]), patch.object(ops,"Compose"), patch.object(subscription_ops,"inspect",return_value={"id":identifier,"status":"PENDING"}), patch.object(subscription_ops,"sql") as execute, redirect_stdout(output):
            subscription_ops.main()
        execute.assert_not_called()
        self.assertFalse(json.loads(output.getvalue())["applied"])
